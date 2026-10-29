package com.cannonworks.block;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Ballistics;
import com.cannonworks.ballistics.ChargeMath;
import com.cannonworks.ballistics.Trajectory;
import com.cannonworks.entity.ShellManager;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.payload.CannonPayload;
import com.cannonworks.payload.FireMode;
import com.cannonworks.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 大炮的装填与射击逻辑。
 *
 * <p>槽位：0 = 发射药（火药），1 = 负载（石碑 / 草垛 / 水桶 / 任意方块）。</p>
 * <p>装药量（{@link #charge}）决定射程，俯仰角与方位角决定弹道。
 * 注意单位：装药量存的是<b>十分之一份</b>（80 = 8.0 份），不是火药个数。
 * 用整数十分之一是为了让 0.1 份的精度能原样走网络包和 NBT，不用和浮点误差较劲。</p>
 */
public class CannonBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_PROPELLANT = 0;
    /** 填充方块（任意方块）。 */
    public static final int SLOT_FILLER = 1;
    /** 货物（石碑 / TNT / 末影珍珠 / 传送板）。 */
    public static final int SLOT_CARGO = 2;
    /** 单发装药上限，单位「十分之一份」：640 = 64.0 份 = 一组火药。 */
    public static final int MAX_CHARGE = ChargeMath.MAX_CHARGE_TENTHS;
    /** 1.0 份装药 = 10 个内部单位（十分之一份）。界面显示和反解都拿它换算。 */
    public static final int TENTHS_PER_CHARGE = ChargeMath.TENTHS_PER_CHARGE;
    /** 1.0 份火药在 45° 俯仰下的射程（格）。 */
    public static final int RANGE_PER_CHARGE = 300;
    /**
     * 存档里 {@code Charge} 的单位记号：2 = 十分之一份（当前格式）。
     *
     * <p>只在「没有这个记号」时才按旧单位迁移，而不是单看 {@code Charge <= 64}——
     * 新存档里 6.4 份（64）也满足那个条件，会被误乘成 64.0 份，炮直接飞天。</p>
     */
    private static final int CHARGE_SCALE_TENTHS = 2;
    public static final int FIRE_COOLDOWN_TICKS = 40;

    private final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int pitch = 45;
    private int yaw;
    private int charge = 80; // 8.0 份
    private long cooldownUntil;
    private long fireGameTime = -1L;
    private FireMode mode = FireMode.CONTACT;

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

    public FireMode getMode() {
        return mode;
    }

    public void setMode(FireMode value) {
        this.mode = value;
        setChanged();
        syncToClients();
    }

    public void toggleMode() {
        setMode(mode.next());
    }

    public int getCooldown() {
        if (level == null || cooldownUntil <= 0) {
            return 0;
        }
        return (int) Math.max(0, cooldownUntil - level.getGameTime());
    }

    /**
     * 最近一次开火的世界时间。客户端渲染器用它驱动后坐和炮口焰；
     * 没有开过火时为 -1。
     */
    public long getFireGameTime() {
        return fireGameTime;
    }

    /** 俯仰角会影响火药效率：低抛省药、高抛费药。 */
    public double getRangeExact() {
        return getRangeExact(charge);
    }

    /**
     * @param usedChargeTenths 实际参与计算装药，单位「十分之一份」
     */
    public double getRangeExact(int usedChargeTenths) {
        double efficiency = Ballistics.pitchEfficiency(pitch);
        double charges = ChargeMath.toCharges(usedChargeTenths);
        // 不设玩法上限：能打多远由装药与俯仰角决定，只保留防溢出的安全值
        return Math.min(charges * RANGE_PER_CHARGE * efficiency, Ballistics.MAX_RANGE);
    }

    public int getRange() {
        return (int) Math.round(getRangeExact());
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
        this.charge = ChargeMath.clamp(value);
        setChanged();
    }

    /**
     * 键盘/滑条微调。
     *
     * <p>三个 delta 跟字段同单位：角度是度，装药是<b>十分之一份</b>。
     * 界面按 PgUp/PgDn 一次发 ±{@link #TENTHS_PER_CHARGE}（=1.0 份），不然 0..640 档要按 640 次。</p>
     */
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
        // 火药按「整份」消耗、按请求的装药算射程；规则全在 ChargeMath 里，有单元测试守着
        int usedItems = ChargeMath.consumedItems(charge, propellant.getCount());
        int usedChargeTenths = ChargeMath.effectiveChargeTenths(charge, propellant.getCount());

        ItemStack fillerStack = inventory.getStackInSlot(SLOT_FILLER);
        ItemStack cargoStack = inventory.getStackInSlot(SLOT_CARGO);
        CannonPayload payload = resolvePayload(fillerStack, cargoStack);

        Vec3 direction = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 origin = CannonGeometry.muzzle(worldPosition, yaw, pitch);
        double range = getRangeExact(usedChargeTenths);
        Vec3 target = origin.add(direction.scale(range));
        Trajectory trajectory = Ballistics.create(origin, target, pitch);

        ItemStack remainder = cargoStack.isEmpty() ? ItemStack.EMPTY : cargoStack.getCraftingRemainingItem();

        propellant.shrink(usedItems);
        // 填充方块：整叠装、整叠打（1:1 铺到落点）
        if (payload.hasFiller()) {
            inventory.extractItem(SLOT_FILLER, payload.fillerCount(), false);
        }
        // 货物：一次消耗一个
        if (payload.hasCargo()) {
            inventory.extractItem(SLOT_CARGO, 1, false);
        }
        if (!remainder.isEmpty()) {
            if (player == null || !player.getInventory().add(remainder)) {
                Block.popResource(level, worldPosition, remainder);
            }
        }

        ShellManager.launch((ServerLevel) level, trajectory, player != null ? player.getUUID() : null, payload, mode);
        cooldownUntil = level.getGameTime() + FIRE_COOLDOWN_TICKS;
        fireGameTime = level.getGameTime();
        playFireEffects((ServerLevel) level, origin);
        setChanged();
        syncToClients();

        CannonWorks.LOGGER.info("Cannon fired at {} by {}: charge {} ({} powder) range {} pitch {} yaw {} payload {}",
                worldPosition, player != null ? player.getName().getString() : "console",
                String.format("%.1f", ChargeMath.toCharges(usedChargeTenths)), usedItems,
                String.format("%.0f", trajectory.range()), pitch, yaw, payload.describe());
        return true;
    }

    /** 重炮开火的全服可见表现：大团烟、火焰、冲击尘和低频巨响。 */
    private void playFireEffects(ServerLevel server, Vec3 muzzle) {
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                muzzle.x, muzzle.y, muzzle.z, 80, 1.8, 1.2, 1.8, 0.025);
        server.sendParticles(ParticleTypes.LARGE_SMOKE,
                muzzle.x, muzzle.y, muzzle.z, 50, 1.5, 0.9, 1.5, 0.04);
        server.sendParticles(ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 40, 0.9, 0.6, 0.9, 0.05);
        server.sendParticles(ParticleTypes.CLOUD,
                muzzle.x, muzzle.y, muzzle.z, 35, 1.8, 0.6, 1.8, 0.03);
        server.sendParticles(ParticleTypes.EXPLOSION,
                muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
        server.playSound(null, muzzle.x, muzzle.y, muzzle.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 8.0f, 0.45f);
        server.playSound(null, muzzle.x, muzzle.y, muzzle.z,
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 2.5f, 0.55f);
    }

    /** 由「填充槽 + 货物槽」组合出一发炮弹的负载。 */
    public static CannonPayload resolvePayload(ItemStack fillerStack, ItemStack cargoStack) {
        BlockState fillerState = null;
        int fillerCount = 0;
        if (!fillerStack.isEmpty()) {
            if (fillerStack.getItem() instanceof BlockItem blockItem) {
                fillerState = blockItem.getBlock().defaultBlockState();
                fillerCount = Math.min(fillerStack.getCount(), 64);
            } else if (fillerStack.is(Items.WATER_BUCKET)) {
                fillerState = Blocks.WATER.defaultBlockState();
                fillerCount = Math.min(fillerStack.getCount(), 16);
            } else if (fillerStack.is(Items.LAVA_BUCKET)) {
                fillerState = Blocks.LAVA.defaultBlockState();
                fillerCount = Math.min(fillerStack.getCount(), 16);
            }
        }

        CannonPayload.Cargo cargo = CannonPayload.Cargo.NONE;
        String cargoName = null;
        BlockState cargoState = null;
        if (isWaystoneItem(cargoStack)) {
            cargo = CannonPayload.Cargo.WAYSTONE;
            cargoName = cargoStack.has(DataComponents.CUSTOM_NAME) ? cargoStack.getHoverName().getString() : null;
            if (cargoStack.getItem() instanceof BlockItem blockItem) {
                cargoState = blockItem.getBlock().defaultBlockState();
            }
        } else if (cargoStack.is(Items.TNT)) {
            cargo = CannonPayload.Cargo.TNT;
        } else if (cargoStack.is(Items.ENDER_PEARL)) {
            cargo = CannonPayload.Cargo.ENDER_PEARL;
        } else if (isWarpPlateItem(cargoStack)) {
            cargo = CannonPayload.Cargo.WARP_PLATE;
        } else if (cargoStack.is(Items.WATER_BUCKET)) {
            cargo = CannonPayload.Cargo.WATER;
        } else if (cargoStack.is(Items.LAVA_BUCKET)) {
            cargo = CannonPayload.Cargo.LAVA;
        }
        return CannonPayload.of(fillerState, fillerCount, cargo, cargoName, cargoState);
    }
    /** Filler slot accepts blocks and fluid buckets. */
    public static boolean isValidFiller(ItemStack stack) {
        return stack.isEmpty()
                || stack.getItem() instanceof BlockItem
                || stack.is(Items.WATER_BUCKET)
                || stack.is(Items.LAVA_BUCKET);
    }

    /** Cargo slot only accepts functional items. */
    public static boolean isValidCargo(ItemStack stack) {
        return stack.isEmpty()
                || stack.is(Items.TNT)
                || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.WATER_BUCKET)
                || stack.is(Items.LAVA_BUCKET)
                || isWaystoneItem(stack)
                || isWarpPlateItem(stack);
    }

    private static boolean isWarpPlateItem(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "waystones".equals(id.getNamespace()) && id.getPath().contains("warp_plate");
    }

    /**
     * 给货物槽里的石碑改名（界面命名框用，省得跑铁砧）。
     *
     * <p>名字写在物品自己的 {@code display.Name} 上：拿下来还是这个名字，换台炮也认。
     * 传空字符串就恢复默认，落点石碑会叫「空投点 x, z」。</p>
     *
     * @return 是否真的改了（槽里不是石碑就返回 false，什么都不做）
     */
    public boolean renameCargo(String name) {
        ItemStack stack = inventory.getStackInSlot(SLOT_CARGO);
        if (!isWaystoneItem(stack)) {
            return false;
        }
        // 客户端会 trim 一遍，这里再来一次：命令/别的 mod 也可能直接调进来，
        // 全是空格的名字在落点石碑列表里就是一个看不见的空条目，很难排查。
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(trimmed));
        }
        setChanged();
        return true;
    }

    /** 不硬依赖 Waystones：按注册名判断，避免类加载。 */
    public static boolean isWaystoneItem(ItemStack stack) {
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

    // ---- 存档 ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Pitch", pitch);
        tag.putInt("Yaw", yaw);
        tag.putInt("Charge", charge);
        // 单位记号：没有它就只能靠猜新旧格式，见 CHARGE_SCALE_TENTHS 的注释
        tag.putInt("ChargeScale", CHARGE_SCALE_TENTHS);
        tag.putLong("CooldownUntil", cooldownUntil);
        tag.putLong("FireGameTime", fireGameTime);
        tag.putString("Mode", mode.name());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            // 旧存档只有 2 个槽，ItemStackHandler.deserializeNBT 会按 NBT 里的 Size 改槽位数，
            // 导致新界面的第 3 个槽越界崩溃。这里强制按当前槽位数反序列化（物品按索引保留）。
            CompoundTag inv = tag.getCompound("Inventory").copy();
            inv.putInt("Size", inventory.getSlots());
            inventory.deserializeNBT(registries, inv);
        }
        pitch = tag.contains("Pitch") ? tag.getInt("Pitch") : pitch;
        yaw = tag.contains("Yaw") ? tag.getInt("Yaw") : yaw;
        int storedCharge = tag.contains("Charge") ? tag.getInt("Charge") : charge;
        // 旧存档：没有 ChargeScale 记号、值不超过旧上限 → 按整数份 ×10；规则见 ChargeMath#migrateCharge
        charge = ChargeMath.migrateCharge(storedCharge, tag.contains("ChargeScale"));
        cooldownUntil = tag.getLong("CooldownUntil");
        fireGameTime = tag.contains("FireGameTime") ? tag.getLong("FireGameTime") : -1L;
        if (tag.contains("Mode")) {
            try {
                mode = FireMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException ignored) {
                mode = FireMode.CONTACT;
            }
        }
    }

    // ---- 同步给客户端（渲染炮管角度） ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Pitch", pitch);
        tag.putInt("Yaw", yaw);
        tag.putLong("FireGameTime", fireGameTime);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        pitch = tag.getInt("Pitch");
        yaw = tag.getInt("Yaw");
        fireGameTime = tag.contains("FireGameTime") ? tag.getLong("FireGameTime") : -1L;
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
