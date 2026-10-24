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
 * <p>布局（176×222）：</p>
 * <pre>
 *   0                88
 *   ┌──────────────────────────────┐
 *   │ 大炮                          │  y=6
 *   │ 火药 [ ]   俯仰 45°   [-] [+] │  y=24
 *   │ 负载 [ ]   方位 90°   [-] [+] │  y=46
 *   │            装药 8/40  [-] [+] │  y=68
 *   │            射程 / 飞行时间      │  y=90
 *   │            [    点火    ]      │  y=116
 *   ├──────────────────────────────┤  y=137
 *   │ 玩家背包                       │  y=140
 *   └──────────────────────────────┘
 * </pre>
 *
 * <p>槽位方框按 {@link Slot} 的真实坐标绘制，不存在错位。</p>
 */
public class CannonScreen extends AbstractContainerScreen<CannonMenu> {

    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;
    private static final int TEXT = 0x404040;
    private static final int TEXT_GOOD = 0x1F5F1F;
    private static final int TEXT_WARN = 0x9F1F1F;

    private static final int ROW_PITCH = 24;
    private static final int ROW_YAW = 46;
    private static final int ROW_CHARGE = 68;
    private static final int TEXT_X = 64;
    private static final int BUTTON_MINUS_X = 132;
    private static final int BUTTON_PLUS_X = 152;

    public CannonScreen(CannonMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(button("-", BUTTON_MINUS_X, ROW_PITCH - 4, -10, 0, 0, false));
        addRenderableWidget(button("+", BUTTON_PLUS_X, ROW_PITCH - 4, 10, 0, 0, false));
        addRenderableWidget(button("-", BUTTON_MINUS_X, ROW_YAW - 4, 0, -15, 0, false));
        addRenderableWidget(button("+", BUTTON_PLUS_X, ROW_YAW - 4, 0, 15, 0, false));
        addRenderableWidget(button("-", BUTTON_MINUS_X, ROW_CHARGE - 4, 0, 0, -1, false));
        addRenderableWidget(button("+", BUTTON_PLUS_X, ROW_CHARGE - 4, 0, 0, 1, false));

        addRenderableWidget(Button.builder(Component.literal("点火"), b -> send(0, 0, 0, true))
                .bounds(leftPos + 64, topPos + 116, 88, 20)
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

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.fill(x, y, x + imageWidth, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + imageHeight, PANEL_LIGHT);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_DARK);

        // 机器区与背包区的分隔线
        graphics.fill(x + 7, y + 137, x + imageWidth - 7, y + 138, PANEL_DARK);

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

        graphics.drawString(font, title, 8, 6, TEXT, false);

        // 左侧槽位说明
        graphics.drawString(font, "火药", 8, 29, TEXT, false);
        graphics.drawString(font, "负载", 8, 61, TEXT, false);

        // 右侧三行参数
        graphics.drawString(font, "俯仰 " + pitch + "°", TEXT_X, ROW_PITCH, TEXT, false);
        graphics.drawString(font, "方位 " + yaw + "°", TEXT_X, ROW_YAW, TEXT, false);
        graphics.drawString(font, "装药 " + charge + " / " + CannonBlockEntity.MAX_CHARGE,
                TEXT_X, ROW_CHARGE, TEXT, false);

        // 只读信息
        graphics.drawString(font, "射程 " + range + " 格", TEXT_X, 90, TEXT_GOOD, false);
        graphics.drawString(font, String.format("飞行 %.1f 秒", flight / 20.0), TEXT_X, 102, TEXT_GOOD, false);

        // 背包标题与状态
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
        if (cooldown > 0) {
            graphics.drawString(font, String.format("装填中 %.1fs", cooldown / 20.0), 8, 112, TEXT_WARN, false);
        }
    }
}
