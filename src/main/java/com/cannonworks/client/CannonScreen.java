package com.cannonworks.client;

import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.network.CannonControlPacket;
import com.cannonworks.network.CannonNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 大炮的装填/瞄准界面。
 *
 * <p>背景不用位图，全部按 {@link Slot} 的真实坐标程序化绘制：
 * 槽位方框和真正能放东西的格子必然重合，不会再出现「画的框和实际位置对不上」。</p>
 */
public class CannonScreen extends AbstractContainerScreen<CannonMenu> {

    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;

    public CannonScreen(CannonMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        // 俯仰
        addRenderableWidget(button("-", 108, 16, -10, 0, 0, false));
        addRenderableWidget(button("+", 130, 16, 10, 0, 0, false));
        // 方位
        addRenderableWidget(button("-", 108, 36, 0, -15, 0, false));
        addRenderableWidget(button("+", 130, 36, 0, 15, 0, false));
        // 装药
        addRenderableWidget(button("-", 108, 56, 0, 0, -1, false));
        addRenderableWidget(button("+", 130, 56, 0, 0, 1, false));

        addRenderableWidget(Button.builder(Component.literal("点火"), b -> send(0, 0, 0, true))
                .bounds(leftPos + 64, topPos + 112, 84, 20)
                .build());
    }

    private Button button(String label, int x, int y, int pitch, int yaw, int charge, boolean fire) {
        return Button.builder(Component.literal(label), b -> send(pitch, yaw, charge, fire))
                .bounds(leftPos + x, topPos + y, 18, 16)
                .build();
    }

    private void send(int pitchDelta, int yawDelta, int chargeDelta, boolean fire) {
        CannonNetwork.CHANNEL.sendToServer(new CannonControlPacket(
                menu.getBlockEntity().getBlockPos(), pitchDelta, yawDelta, chargeDelta, fire));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 面板与描边
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.fill(x, y, x + imageWidth, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + imageHeight, PANEL_LIGHT);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_DARK);

        // 玩家背包分隔线
        graphics.fill(x + 7, y + 77, x + imageWidth - 7, y + 78, PANEL_DARK);

        // 所有槽位：直接用 Slot 的真实坐标，天然对齐
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
            graphics.fill(sx, sy, sx + 18, sy + 1, SLOT_SHADE);
            graphics.fill(sx, sy, sx + 1, sy + 18, SLOT_SHADE);
            graphics.fill(sx + 17, sy, sx + 18, sy + 18, PANEL_LIGHT);
            graphics.fill(sx, sy + 17, sx + 18, sy + 18, PANEL_LIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int pitch = menu.getData(CannonMenu.DATA_PITCH);
        int yaw = menu.getData(CannonMenu.DATA_YAW);
        int charge = menu.getData(CannonMenu.DATA_CHARGE);
        int range = menu.getData(CannonMenu.DATA_RANGE);
        int flight = menu.getData(CannonMenu.DATA_FLIGHT);
        int cooldown = menu.getData(CannonMenu.DATA_COOLDOWN);

        graphics.drawString(font, title, 8, 6, 0x404040, false);
        graphics.drawString(font, "俯仰 " + pitch + "°", 64, 20, 0x404040, false);
        graphics.drawString(font, "方位 " + yaw + "°", 64, 40, 0x404040, false);
        graphics.drawString(font, "装药 " + charge + " / " + CannonBlockEntity.MAX_CHARGE,
                64, 60, 0x404040, false);
        graphics.drawString(font, "射程 " + range + " 格", 64, 78, 0x1f5f1f, false);
        graphics.drawString(font, String.format("飞行 %.1f 秒", flight / 20.0), 64, 90, 0x1f5f1f, false);
        if (cooldown > 0) {
            graphics.drawString(font, String.format("装填中 %.1fs", cooldown / 20.0), 64, 136, 0x9f1f1f, false);
        }
    }
}
