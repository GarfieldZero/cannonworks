package com.cannonworks.block;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Ballistics;
import com.cannonworks.ballistics.Trajectory;
import com.cannonworks.entity.ShellManager;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.payload.CannonPayload;
import com.cannonworks.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 大炮的装填与射击逻辑。
 *
 * <p>槽位：0 = 发射药（火药），1 = 负载（石碑 / 草垛 / 水桶 / 任意方块）。</p>
 * <p>装药量（{@link #charge}）是「这次用几份火药」，决定射程；俯仰角与方位角决定弹道。</p>
 */
public class CannonBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_PROPELLANT = 0;
    public static final int SLOT_PAYLOAD = 1;
    public static final int MAX_CHARGE = 40;
    public static final int RANGE_PER_CHARGE = 300;
    public static final int FIRE_COOLDOWN_TICKS = 40;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int pitch = 45;
    private int yaw;
    private int charge = 8;
    private long cooldownUntil;

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CANNON.get(), pos, state);
        this.yaw = yawFromFacing(state.getValue(CannonBlock.FACING));
    }

    private static int yawFromFacing(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public int getPitch() {
        return pitch;
    }

    public int getYaw() {
        return yaw;
    }

    public int getCharge() {
        return charge;
    }

    public int getCooldown() {
        if (level == null || cooldownUntil <= 0) {
            return 0;
        }
        return (int) Math.max(0, cooldownUntil - level.getGameTime());
    }

    public int getRange() {
        return Math.min(charge * RANGE_PER_CHARGE, (int) Ballistics.MAX_RANGE);
    }

    public int getFlightTicks() {
        return Ballistics.flightTicksFor(Math.max(1, getRange()), pitch);
    }

    public void setPitch(int value) {
        this.pitch = Math.max((int) Ballistics.MIN_PITCH, Math.min((int) Ballistics.MAX_PITCH, value));
        syncToClients();
    }

    public void setYaw(int value) {
        this.yaw = Math.floorMod(value, 360);
        syncToClients();
    }

    public void setCharge(int value) {
        this.charge = Math.max(0, Math.min(MAX_CHARGE, value));
        setChanged();
    }

    public void adjust(int pitchDelta, int yawDelta, int chargeDelta) {
        setPitch(pitch + pitchDelta);
        setYaw(yaw + yawDelta);
        setCharge(charge + chargeDelta);
    }

    // 冷却用「到期时间戳」而不是计数器：方块实体不 tick 也不会卡住

    /** 是否满足开火条件（给 GUI 显示用）。 */
    public boolean canFire() {
        if (getCooldown() > 0 || level == null || level.isClientSide) {
            return false;
        }
        ItemStack propellant = inventory.getStackInSlot(SLOT_PROPELLANT);
        return propellant.is(Items.GUNPOWDER) && propellant.getCount() > 0 && charge > 0;
    }

    /**
     * 开火：扣火药与负载，算出弹道，交给 {@link ShellManager}。
     *
     * @return 是否真的打出去了
     */
    public boolean fire(@Nullable Player player) {
        if (!canFire()) {
            return false;
        }
        ItemStack propellant = inventory.getStackInSlot(SLOT_PROPELLANT);
        int usedPowder = Math.min(charge, propellant.getCount());

        ItemStack payloadStack = inventory.getStackInSlot(SLOT_PAYLOAD);
        CannonPayload payload = resolvePayload(payloadStack);

        Vec3 direction = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 origin = Vec3.atCenterOf(worldPosition).add(0.0, 0.9, 0.0).add(direction.scale(1.6));
        double range = Math.min(usedPowder * (double) RANGE_PER_CHARGE, Ballistics.MAX_RANGE);
        Vec3 target = origin.add(direction.scale(range));
        Trajectory trajectory = Ballistics.create(origin, target, pitch);

        // 先算好掉落物（水桶会留下空桶），再扣物品
        ItemStack remainder = payloadStack.isEmpty() ? ItemStack.EMPTY : payloadStack.getCraftingRemainingItem();
        int payloadCost = payload.type() == CannonPayload.Type.FILLER ? payloadStack.getCount() : 1;

        propellant.shrink(usedPowder);
        if (!payloadStack.isEmpty()) {
            inventory.extractItem(SLOT_PAYLOAD, payloadCost, false);
        }
        if (!remainder.isEmpty()) {
            if (player == null || !player.getInventory().add(remainder)) {
                Block.popResource(level, worldPosition, remainder);
            }
        }

        ShellManager.launch((ServerLevel) level, trajectory, player != null ? player.getUUID() : null, payload);
        cooldownUntil = level.getGameTime() + FIRE_COOLDOWN_TICKS;
        setChanged();
        syncToClients();

        CannonWorks.LOGGER.info("Cannon fired at {} by {}: range {} pitch {} yaw {} payload {}",
                worldPosition, player != null ? player.getName().getString() : "console", String.format("%.0f", trajectory.range()), pitch, yaw,
                payload.describe());
        return true;
    }

    /** 把槽位里的物品翻译成负载类型。任意方块 = 填充物（空投建材）。 */
    public static CannonPayload resolvePayload(ItemStack stack) {
        if (stack.isEmpty()) {
            return CannonPayload.NONE;
        }
        if (isWaystoneItem(stack)) {
            String name = stack.hasCustomHoverName() ? stack.getHoverName().getString() : null;
            // 记下具体是哪种石碑：背包里放的是苔石版，落地就该是苔石版
            BlockState style = stack.getItem() instanceof BlockItem blockItem
                    ? blockItem.getBlock().defaultBlockState()
                    : null;
            return CannonPayload.waystone(name, style);
        }
        if (stack.is(Items.HAY_BLOCK)) {
            return CannonPayload.hay();
        }
        if (stack.is(Items.WATER_BUCKET)) {
            return CannonPayload.water();
        }
        // TNT 也是 BlockItem，必须排在「任意方块 → 填充物」之前
        if (stack.is(Items.TNT)) {
            return CannonPayload.tnt();
        }
        if (stack.is(Items.ENDER_PEARL)) {
            return CannonPayload.enderPearl();
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            return CannonPayload.filler(blockItem.getBlock().defaultBlockState(), Math.min(stack.getCount(), 64));
        }
        return CannonPayload.NONE;
    }

    /** 不硬依赖 Waystones：按注册名判断，避免类加载。 */
    private static boolean isWaystoneItem(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "waystones".equals(id.getNamespace())
                && (id.getPath().contains("waystone") || id.getPath().contains("sharestone"));
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack);
            }
        }
    }

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- 渲染范围（炮管会伸出方块外） ----

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(3.0);
    }

    // ---- 存档 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Pitch", pitch);
        tag.putInt("Yaw", yaw);
        tag.putInt("Charge", charge);
        tag.putLong("CooldownUntil", cooldownUntil);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        pitch = tag.contains("Pitch") ? tag.getInt("Pitch") : pitch;
        yaw = tag.contains("Yaw") ? tag.getInt("Yaw") : yaw;
        charge = tag.contains("Charge") ? tag.getInt("Charge") : charge;
        cooldownUntil = tag.getLong("CooldownUntil");
    }

    // ---- 同步给客户端（渲染炮管角度） ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Pitch", pitch);
        tag.putInt("Yaw", yaw);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        pitch = tag.getInt("Pitch");
        yaw = tag.getInt("Yaw");
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    // ---- 菜单 ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cannonworks.cannon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new CannonMenu(windowId, playerInventory, this);
    }

    /** 给 GUI 的只读摘要。 */
    public List<String> summary() {
        return List.of(
                "射程 " + getRange() + " 格",
                "飞行 " + String.format("%.1f", getFlightTicks() / 20.0) + " 秒",
                "俯仰 " + pitch + "°  方位 " + yaw + "°");
    }
}
