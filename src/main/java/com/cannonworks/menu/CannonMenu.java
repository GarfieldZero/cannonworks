package com.cannonworks.menu;

import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.payload.FireMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.cannonworks.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 大炮的装填界面。
 *
 * <p>左侧两个槽：0 = 火药，1 = 负载。右侧是俯仰/方位/装药与开火按钮（在 {@code CannonScreen} 里）。</p>
 * <p>数值同步走 {@link ContainerData}：0 俯仰、1 方位、2 装药、3 冷却、4 射程、5 飞行 tick。</p>
 */
public class CannonMenu extends AbstractContainerMenu {

    public static final int DATA_PITCH = 0;
    public static final int DATA_YAW = 1;
    public static final int DATA_CHARGE = 2;
    public static final int DATA_COOLDOWN = 3;
    public static final int DATA_RANGE = 4;
    public static final int DATA_FLIGHT = 5;
    public static final int DATA_MODE = 6;

    private final CannonBlockEntity blockEntity;
    private final ContainerData data;

    public CannonMenu(int windowId, Inventory playerInventory, CannonBlockEntity blockEntity) {
        super(ModMenus.CANNON.get(), windowId);
        this.blockEntity = blockEntity;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PITCH -> blockEntity.getPitch();
                    case DATA_YAW -> blockEntity.getYaw();
                    case DATA_CHARGE -> blockEntity.getCharge();
                    case DATA_COOLDOWN -> blockEntity.getCooldown();
                    case DATA_RANGE -> blockEntity.getRange();
                    case DATA_FLIGHT -> blockEntity.getFlightTicks();
                    case DATA_MODE -> blockEntity.getMode().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case DATA_PITCH -> blockEntity.setPitch(value);
                    case DATA_YAW -> blockEntity.setYaw(value);
                    case DATA_CHARGE -> blockEntity.setCharge(value);
                    case DATA_MODE -> blockEntity.setMode(FireMode.values()[Math.max(0, Math.min(1, value))]);
                    default -> {
                    }
                }
            }

            @Override
            public int getCount() {
                return 7;
            }
        };

        // 机器区：左侧两个槽（火药 / 负载），坐标与 CannonScreen 的排版一一对应
        addSlot(new SlotItemHandler(blockEntity.getInventory(), CannonBlockEntity.SLOT_PROPELLANT, 30, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.GUNPOWDER);
            }
        });
        addSlot(new SlotItemHandler(blockEntity.getInventory(), CannonBlockEntity.SLOT_FILLER, 30, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CannonBlockEntity.isValidFiller(stack);
            }
        });
        addSlot(new SlotItemHandler(blockEntity.getInventory(), CannonBlockEntity.SLOT_CARGO, 30, 80) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CannonBlockEntity.isValidCargo(stack);
            }
        });

        // 玩家背包与快捷栏：整体下移到 y=140 / y=198，给上面的信息区和点火按钮留出空间
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 216));
        }

        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
    }

    public CannonBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = 3;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
