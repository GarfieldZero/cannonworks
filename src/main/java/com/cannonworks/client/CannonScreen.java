package com.cannonworks.client;

import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.network.CannonAimPacket;
import com.cannonworks.network.CannonControlPacket;
import com.cannonworks.network.CannonNetwork;
import com.cannonworks.payload.FireMode;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * 大炮的装填/瞄准界面。
 *
 * <p>瞄准交互分两档：滑条拖动（大范围）+ 键盘微调（1°，Shift 5°）。</p>
 *
 * <p>注意：{@link AbstractContainerScreen} 自己重写了 {@code mouseDragged} 用于拖拽物品，
 * 且<b>不会转发给父类</b>，所以滑条在容器界面里天然拖不动。这里由本界面接管点击/拖拽，
 * 直接把鼠标 X 映射成滑条值。</p>
 */
public class CannonScreen extends AbstractContainerScreen<CannonMenu> {

    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;
    private static final int TEXT = 0x404040;
    private static final int TEXT_GOOD = 0x1F5F1F;
    private static final int TEXT_HINT = 0x6A6A6A;
    private static final int TEXT_WARN = 0x9F1F1F;

    private static final int ROW_PITCH = 16;
    private static final int ROW_YAW = 38;
    private static final int ROW_CHARGE = 60;
    private static final int SLIDER_X = 56;
    private static final int SLIDER_WIDTH = 104;
    private static final int SEPARATOR_Y = 155;

    private AimSlider pitchSlider;
    private AimSlider yawSlider;
    private AimSlider chargeSlider;
    private AimSlider activeSlider;
    private Button modeButton;
    /** 本地刚改过值时，先别被服务端的旧数据回拉。 */
    private int holdSync;

    public CannonScreen(CannonMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 240;
        this.inventoryLabelY = 147;
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
        modeButton = Button.builder(modeLabel(), b -> toggleMode())
                .bounds(leftPos + SLIDER_X, topPos + 136, SLIDER_WIDTH, 16)
                .build();
        addRenderableWidget(modeButton);
    }

    private void sendAim() {
        CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(
                menu.getBlockEntity().getBlockPos(),
                pitchSlider.intValue(), yawSlider.intValue(), chargeSlider.intValue(), currentMode()));
        holdSync = 6;
    }

    private FireMode currentMode() {
        FireMode[] values = FireMode.values();
        return values[Mth.clamp(menu.getData(CannonMenu.DATA_MODE), 0, values.length - 1)];
    }

    private Component modeLabel() {
        return Component.literal("模式：" + currentMode().label());
    }

    private void toggleMode() {
        FireMode next = currentMode().next();
        CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(
                menu.getBlockEntity().getBlockPos(),
                pitchSlider.intValue(), yawSlider.intValue(), chargeSlider.intValue(), next));
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

    // ---- 滑条：自己接管点击与拖拽（容器界面不会把 drag 转发给控件） ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (GuiEventListener child : children()) {
                if (child instanceof AimSlider slider && slider.visible && slider.isMouseOver(mouseX, mouseY)) {
                    activeSlider = slider;
                    slider.dragTo(mouseX);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeSlider != null && button == 0) {
            activeSlider.dragTo(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        activeSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
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
        pitchSlider.syncFromServer(menu.getData(CannonMenu.DATA_PITCH));
        yawSlider.syncFromServer(menu.getData(CannonMenu.DATA_YAW));
        chargeSlider.syncFromServer(menu.getData(CannonMenu.DATA_CHARGE));
        modeButton.setMessage(modeLabel());
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

        graphics.fill(x + 7, y + SEPARATOR_Y, x + imageWidth - 7, y + SEPARATOR_Y + 1, PANEL_DARK);

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
        graphics.drawString(font, "填充", 8, 57, TEXT, false);
        graphics.drawString(font, "货物", 8, 85, TEXT, false);

        graphics.drawString(font, "射程 " + range + " 格", SLIDER_X, 84, TEXT_GOOD, false);
        graphics.drawString(font, String.format("飞行 %.1f 秒", flight / 20.0), SLIDER_X, 96, TEXT_GOOD, false);

        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
        if (cooldown > 0) {
            graphics.drawString(font, String.format("装填中 %.1fs", cooldown / 20.0), 8, 112, TEXT_WARN, false);
        }
    }

    /** 滑条：拖动由界面接管，值变化时发绝对值包。 */
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

        /** 鼠标 X → 滑条值（由界面在点击/拖拽时调用，绕开容器界面的拖拽吞事件）。 */
        void dragTo(double mouseX) {
            double newValue = Mth.clamp((mouseX - (getX() + 4)) / (getWidth() - 8.0), 0.0, 1.0);
            this.value = newValue;
            updateMessage();
            int current = intValue();
            if (current != lastSent) {
                lastSent = current;
                onChanged.accept(current);
            }
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + " " + intValue() + unit));
        }

        @Override
        protected void applyValue() {
            int current = intValue();
            if (current != lastSent) {
                lastSent = current;
                onChanged.accept(current);
            }
        }

        void setLocal(int value) {
            int clamped = clamp(value);
            this.value = ratio(clamped, min, max);
            this.lastSent = clamped;
            updateMessage();
        }

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
