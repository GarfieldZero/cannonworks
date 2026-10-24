package com.cannonworks.client;

import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.network.CannonAimPacket;
import com.cannonworks.network.CannonControlPacket;
import com.cannonworks.network.CannonNetwork;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * 大炮的装填/瞄准界面。
 *
 * <p>瞄准交互分两档：</p>
 * <ul>
 *   <li><b>滑条</b>：大范围拖动（俯仰 5–85°、方位 0–359°、装药 0–40）；</li>
 *   <li><b>键盘</b>：↑↓ 微调俯仰、←→ 微调方位、PageUp/PageDown 微调装药，
 *       每次 1°，按住 Shift 每次 5°。</li>
 * </ul>
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

    private static final int ROW_PITCH = 16;
    private static final int ROW_YAW = 38;
    private static final int ROW_CHARGE = 60;
    private static final int SLIDER_X = 56;
    private static final int SLIDER_WIDTH = 104;

    private AimSlider pitchSlider;
    private AimSlider yawSlider;
    private AimSlider chargeSlider;
    /** 本地刚改过值时，先别被服务端的旧数据回拉。 */
    private int holdSync;

    public CannonScreen(CannonMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + SLIDER_X;
        pitchSlider = new AimSlider(x, topPos + ROW_PITCH, SLIDER_WIDTH, "俯仰", "°",
                5, 85, menu.getData(CannonMenu.DATA_PITCH), v -> sendAim());
        yawSlider = new AimSlider(x, topPos + ROW_YAW, SLIDER_WIDTH, "方位", "°",
                0, 359, menu.getData(CannonMenu.DATA_YAW), v -> sendAim());
        chargeSlider = new AimSlider(x, topPos + ROW_CHARGE, SLIDER_WIDTH, "装药", "",
                0, CannonBlockEntity.MAX_CHARGE, menu.getData(CannonMenu.DATA_CHARGE), v -> sendAim());

        addRenderableWidget(pitchSlider);
        addRenderableWidget(yawSlider);
        addRenderableWidget(chargeSlider);

        addRenderableWidget(Button.builder(Component.literal("点火"), b -> fire())
                .bounds(leftPos + SLIDER_X, topPos + 112, SLIDER_WIDTH, 20)
                .build());
    }

    private void sendAim() {
        CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(
                menu.getBlockEntity().getBlockPos(),
                pitchSlider.intValue(), yawSlider.intValue(), chargeSlider.intValue()));
        holdSync = 6;
    }

    private void fire() {
        CannonNetwork.CHANNEL.sendToServer(new CannonControlPacket(
                menu.getBlockEntity().getBlockPos(), 0, 0, 0, true));
    }

    /** 键盘微调：1° 步进，Shift 加速到 5°。 */
    private void nudge(int pitchDelta, int yawDelta, int chargeDelta) {
        CannonNetwork.CHANNEL.sendToServer(new CannonControlPacket(
                menu.getBlockEntity().getBlockPos(), pitchDelta, yawDelta, chargeDelta, false));
        pitchSlider.setLocal(pitchSlider.intValue() + pitchDelta);
        yawSlider.setLocal(Math.floorMod(yawSlider.intValue() + yawDelta, 360));
        chargeSlider.setLocal(chargeSlider.intValue() + chargeDelta);
        holdSync = 4;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                nudge(step, 0, 0);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                nudge(-step, 0, 0);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                nudge(0, -step, 0);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                nudge(0, step, 0);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                nudge(0, 0, step);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                nudge(0, 0, -step);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (holdSync > 0) {
            holdSync--;
            return;
        }
        // 服务端数据（按钮/指令/重进界面）回流时，把滑条拉回一致
        pitchSlider.syncFromServer(menu.getData(CannonMenu.DATA_PITCH));
        yawSlider.syncFromServer(menu.getData(CannonMenu.DATA_YAW));
        chargeSlider.syncFromServer(menu.getData(CannonMenu.DATA_CHARGE));
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
        int range = menu.getData(CannonMenu.DATA_RANGE);
        int flight = menu.getData(CannonMenu.DATA_FLIGHT);
        int cooldown = menu.getData(CannonMenu.DATA_COOLDOWN);

        graphics.drawString(font, title, 8, 6, TEXT, false);
        graphics.drawString(font, "火药", 8, 29, TEXT, false);
        graphics.drawString(font, "负载", 8, 61, TEXT, false);

        graphics.drawString(font, "射程 " + range + " 格", SLIDER_X, 84, TEXT_GOOD, false);
        graphics.drawString(font, String.format("飞行 %.1f 秒", flight / 20.0), SLIDER_X, 96, TEXT_GOOD, false);
        graphics.drawString(font, "↑↓ 俯仰  ←→ 方位  PgUp/PgDn 装药（Shift×5）",
                8, 136, 0x6A6A6A, false);

        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
        if (cooldown > 0) {
            graphics.drawString(font, String.format("装填中 %.1fs", cooldown / 20.0), 8, 112, TEXT_WARN, false);
        }
    }

    /** 滑条：拖动时发绝对值包；服务端回流时再拉回一致。 */
    private static final class AimSlider extends AbstractSliderButton {
        private final String label;
        private final String unit;
        private final int min;
        private final int max;
        private final IntConsumer onChanged;
        private int lastSent;

        AimSlider(int x, int y, int width, String label, String unit, int min, int max, int initial,
                  IntConsumer onChanged) {
            super(x, y, width, 16, Component.empty(), ratio(initial, min, max));
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.onChanged = onChanged;
            this.lastSent = clamp(initial);
            updateMessage();
        }

        private static double ratio(int value, int min, int max) {
            return max == min ? 0.0 : (value - min) / (double) (max - min);
        }

        int intValue() {
            return min + (int) Math.round(value * (max - min));
        }

        private int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + " " + intValue() + unit));
        }

        @Override
        protected void applyValue() {
            int value = intValue();
            if (value != lastSent) {
                lastSent = value;
                onChanged.accept(value);
            }
        }

        /** 键盘微调后的乐观更新（不发包）。 */
        void setLocal(int value) {
            int clamped = clamp(value);
            this.value = ratio(clamped, min, max);
            this.lastSent = clamped;
            updateMessage();
        }

        /** 服务端数据回流。 */
        void syncFromServer(int value) {
            int clamped = clamp(value);
            if (intValue() == clamped) {
                return;
            }
            this.value = ratio(clamped, min, max);
            this.lastSent = clamped;
            updateMessage();
        }
    }
}
