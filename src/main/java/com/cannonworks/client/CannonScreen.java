package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Ballistics;
import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.network.CannonAimPacket;
import com.cannonworks.network.CannonControlPacket;
import com.cannonworks.network.CannonNetwork;
import com.cannonworks.payload.FireMode;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * 重炮的装填 / 瞄准界面。
 *
 * <p>布局：左侧三格装填槽，右侧俯仰、方位、装药滑条 + 坐标解算 + 点火。
 * 背景是一张 176×238 的 GUI 图集；滑条和按钮是自绘控件，以便和黑铁/黄铜主题统一。</p>
 *
 * <p>容器界面会吞掉 {@code mouseDragged}，所以滑条的点击/拖动由本界面接管，
 * 直接把鼠标 X 映射成滑条值。</p>
 */
public class CannonScreen extends AbstractContainerScreen<CannonMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CannonWorks.MOD_ID, "textures/gui/cannon.png");
    private static final Component TITLE = Component.translatable("gui.cannonworks.cannon.title");

    private static final int TEXT = 0xFFE2D8C3;
    private static final int TEXT_DIM = 0xFF9B978D;
    private static final int TEXT_MUTED = 0xFF77746C;
    private static final int TEXT_GOOD = 0xFF78D68B;
    private static final int TEXT_GOLD = 0xFFD8B25A;
    private static final int TEXT_WARN = 0xFFE6705A;

    private static final int SLIDER_X = 63;
    private static final int SLIDER_WIDTH = 100;
    private static final int SLIDER_HEIGHT = 14;
    private static final int ROW_PITCH = 24;
    private static final int ROW_YAW = 42;
    private static final int ROW_CHARGE = 60;
    private static final int STATUS_Y = 76;
    private static final int TARGET_Y = 88;
    private static final int FIRE_Y = 108;
    private static final int HINT_Y = 134;

    private AimSlider pitchSlider;
    private AimSlider yawSlider;
    private AimSlider chargeSlider;
    private AimSlider activeSlider;
    private SteelButton modeButton;
    private SteelButton solveButton;
    private SteelButton fireButton;
    private EditBox targetXBox;
    private EditBox targetZBox;
    private int lockX = Integer.MIN_VALUE;
    private int lockZ = Integer.MIN_VALUE;
    private ChangeSource pendingChange = ChangeSource.NONE;
    /** 本地刚改过值时，先别被服务端的旧数据回拉。 */
    private int holdSync;

    private enum ChangeSource { NONE, PITCH, YAW, CHARGE }

    public CannonScreen(CannonMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 238;
        this.inventoryLabelY = 148;
    }

    @Override
    protected void init() {
        super.init();
        int sx = leftPos + SLIDER_X;

        pitchSlider = new AimSlider(sx, topPos + ROW_PITCH, SLIDER_WIDTH, SLIDER_HEIGHT,
                "俯仰", "°", 5, 85, menu.getData(CannonMenu.DATA_PITCH), v -> onSlider(ChangeSource.PITCH));
        yawSlider = new AimSlider(sx, topPos + ROW_YAW, SLIDER_WIDTH, SLIDER_HEIGHT,
                "方位", "°", 0, 359, menu.getData(CannonMenu.DATA_YAW), v -> onSlider(ChangeSource.YAW));
        chargeSlider = new AimSlider(sx, topPos + ROW_CHARGE, SLIDER_WIDTH, SLIDER_HEIGHT,
                "装药", "", 0, CannonBlockEntity.MAX_CHARGE, menu.getData(CannonMenu.DATA_CHARGE),
                v -> onSlider(ChangeSource.CHARGE));

        addRenderableWidget(pitchSlider);
        addRenderableWidget(yawSlider);
        addRenderableWidget(chargeSlider);

        targetXBox = new EditBox(font, leftPos + SLIDER_X, topPos + TARGET_Y, 38, 16,
                Component.literal("X"));
        targetXBox.setMaxLength(9);
        targetXBox.setFilter(text -> text.matches("-?\\d*"));
        targetXBox.setHint(Component.literal("X"));
        targetXBox.setBordered(false);
        targetXBox.setTextColor(0xFFE2D8C3);
        targetXBox.setResponder(text -> updateSolveButton());
        addRenderableWidget(targetXBox);

        targetZBox = new EditBox(font, leftPos + 103, topPos + TARGET_Y, 38, 16,
                Component.literal("Z"));
        targetZBox.setMaxLength(9);
        targetZBox.setFilter(text -> text.matches("-?\\d*"));
        targetZBox.setHint(Component.literal("Z"));
        targetZBox.setBordered(false);
        targetZBox.setTextColor(0xFFE2D8C3);
        targetZBox.setResponder(text -> updateSolveButton());
        addRenderableWidget(targetZBox);

        solveButton = new SteelButton(leftPos + 143, topPos + TARGET_Y, 20, 16,
                Component.literal("解算"), b -> solve(), SteelButton.Style.NORMAL);
        solveButton.setTooltip(Tooltip.create(Component.literal("按目标 X/Z 反解方位与装药")));
        addRenderableWidget(solveButton);

        fireButton = new SteelButton(leftPos + SLIDER_X, topPos + FIRE_Y, SLIDER_WIDTH, 22,
                Component.literal("点火"), b -> fire(), SteelButton.Style.FIRE);
        addRenderableWidget(fireButton);

        modeButton = new SteelButton(leftPos + 9, topPos + 104, 46, 16,
                modeLabel(), b -> toggleMode(), SteelButton.Style.TOGGLE);
        modeButton.setTooltip(Tooltip.create(Component.literal("接触：先炸坑；空爆：不炸坑")));
        addRenderableWidget(modeButton);

        pitchSlider.setTooltip(Tooltip.create(Component.literal("拖动调整俯仰；↑↓ 微调，Shift ×5")));
        yawSlider.setTooltip(Tooltip.create(Component.literal("拖动调整方位；←→ 微调，Shift ×5")));
        chargeSlider.setTooltip(Tooltip.create(Component.literal("拖动调整装药；PgUp/PgDn 微调")));

        updateButtons();
    }

    /** Fire-control solver: computes yaw + charge from a target X/Z, then commits them. */
    private void solve() {
        Integer targetX = parseCoord(targetXBox.getValue());
        Integer targetZ = parseCoord(targetZBox.getValue());
        if (targetX == null || targetZ == null) {
            return;
        }
        BlockPos pos = menu.getBlockEntity().getBlockPos();
        double dx = (targetX + 0.5) - (pos.getX() + 0.5);
        double dz = (targetZ + 0.5) - (pos.getZ() + 0.5);
        double range = Math.sqrt(dx * dx + dz * dz);
        // 俯仰角先由玩家选，解算按该角度的火药效率反推装药
        int pitch = pitchSlider.intValue();
        lockX = targetX;
        lockZ = targetZ;
        double efficiency = Ballistics.pitchEfficiency(pitch);
        int charge = (int) Math.ceil(range / (CannonBlockEntity.RANGE_PER_CHARGE * efficiency));
        if (charge > CannonBlockEntity.MAX_CHARGE) {
            charge = CannonBlockEntity.MAX_CHARGE;
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("目标超出最大射程，已按满装药装订"), true);
            }
        }
        int yaw = (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)));
        yaw = Math.floorMod(yaw, 360);

        CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(pos, pitch, yaw, charge, currentMode()));
        pitchSlider.setLocal(pitch);
        yawSlider.setLocal(yaw);
        chargeSlider.setLocal(charge);
        holdSync = 8;
        updateButtons();
    }

    private static Integer parseCoord(String text) {
        if (text == null || text.isBlank() || "-".equals(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void onSlider(ChangeSource source) {
        pendingChange = source;
        sendAim();
        updateButtons();
    }

    private double lockDistance() {
        BlockPos pos = menu.getBlockEntity().getBlockPos();
        double dx = (lockX + 0.5) - (pos.getX() + 0.5);
        double dz = (lockZ + 0.5) - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean locked() {
        return lockX != Integer.MIN_VALUE;
    }

    /** Keep the locked distance: recompute charge from the new pitch. */
    private void applyChargeForLock() {
        double distance = lockDistance();
        double efficiency = Ballistics.pitchEfficiency(pitchSlider.intValue());
        int charge = (int) Math.ceil(distance / (CannonBlockEntity.RANGE_PER_CHARGE * efficiency));
        chargeSlider.setLocal(Math.max(0, Math.min(CannonBlockEntity.MAX_CHARGE, charge)));
    }

    /** Keep the locked distance: recompute pitch from the new charge. */
    private void applyPitchForLock() {
        int charge = chargeSlider.intValue();
        if (charge <= 0) {
            return;
        }
        double distance = lockDistance();
        double efficiency = distance / (CannonBlockEntity.RANGE_PER_CHARGE * (double) charge);
        double cosPitch = efficiency * Math.cos(Math.toRadians(45.0));
        int pitch;
        if (cosPitch >= Math.cos(Math.toRadians(Ballistics.MIN_PITCH))) {
            pitch = (int) Ballistics.MIN_PITCH;
        } else if (cosPitch <= Math.cos(Math.toRadians(Ballistics.MAX_PITCH))) {
            pitch = (int) Ballistics.MAX_PITCH;
        } else {
            pitch = (int) Math.round(Math.toDegrees(Math.acos(cosPitch)));
        }
        pitchSlider.setLocal(pitch);
    }

    private void sendAim() {
        if (locked()) {
            if (pendingChange == ChangeSource.PITCH) {
                applyChargeForLock();
            } else if (pendingChange == ChangeSource.CHARGE) {
                applyPitchForLock();
            } else if (pendingChange == ChangeSource.YAW) {
                lockX = Integer.MIN_VALUE;
                lockZ = Integer.MIN_VALUE;
            }
        }
        pendingChange = ChangeSource.NONE;
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
        return Component.literal("引信 " + currentMode().label());
    }

    private void toggleMode() {
        FireMode next = currentMode().next();
        CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(
                menu.getBlockEntity().getBlockPos(),
                pitchSlider.intValue(), yawSlider.intValue(), chargeSlider.intValue(), next));
        holdSync = 6;
    }

    private void fire() {
        if (!readyToFire()) {
            return;
        }
        CannonNetwork.CHANNEL.sendToServer(new CannonControlPacket(
                menu.getBlockEntity().getBlockPos(), 0, 0, 0, true));
        CannonShake.trigger(1.5f);
        holdSync = 6;
        if (fireButton != null) {
            fireButton.active = false;
        }
    }

    /** 键盘微调：1° 步进，Shift 加速到 5°。 */
    private void nudge(int pitchDelta, int yawDelta, int chargeDelta) {
        CannonNetwork.CHANNEL.sendToServer(new CannonControlPacket(
                menu.getBlockEntity().getBlockPos(), pitchDelta, yawDelta, chargeDelta, false));
        pitchSlider.setLocal(pitchSlider.intValue() + pitchDelta);
        yawSlider.setLocal(Math.floorMod(yawSlider.intValue() + yawDelta, 360));
        chargeSlider.setLocal(chargeSlider.intValue() + chargeDelta);
        holdSync = 4;
        updateButtons();
    }

    // ---- 滑条：自己接管点击与拖拽（容器界面不会把 drag 转发给控件） ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (GuiEventListener child : children()) {
                if (child instanceof AimSlider slider && slider.visible && slider.isMouseOver(mouseX, mouseY)) {
                    activeSlider = slider;
                    slider.dragTo(mouseX);
                    slider.playDownSound(Minecraft.getInstance().getSoundManager());
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
        // 输入框获得焦点时，方向键/退格归它，别去拧炮
        if (getFocused() instanceof EditBox) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
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
        updateButtons();
    }

    private void updateButtons() {
        if (solveButton != null) {
            solveButton.active = parseCoord(targetXBox.getValue()) != null
                    && parseCoord(targetZBox.getValue()) != null;
        }
        if (fireButton != null) {
            fireButton.active = readyToFire();
        }
    }

    private void updateSolveButton() {
        if (solveButton != null) {
            solveButton.active = parseCoord(targetXBox.getValue()) != null
                    && parseCoord(targetZBox.getValue()) != null;
        }
    }

    private boolean hasPowder() {
        if (menu.getSlot(CannonBlockEntity.SLOT_PROPELLANT) == null) {
            return false;
        }
        ItemStack stack = menu.getSlot(CannonBlockEntity.SLOT_PROPELLANT).getItem();
        return stack.is(Items.GUNPOWDER) && !stack.isEmpty();
    }

    private boolean readyToFire() {
        return menu.getData(CannonMenu.DATA_COOLDOWN) <= 0
                && menu.getData(CannonMenu.DATA_CHARGE) > 0
                && hasPowder();
    }

    private String statusText() {
        if (menu.getData(CannonMenu.DATA_COOLDOWN) > 0) {
            return String.format("装填中 %.1fs", menu.getData(CannonMenu.DATA_COOLDOWN) / 20.0);
        }
        if (!hasPowder()) {
            return "缺少火药";
        }
        if (menu.getData(CannonMenu.DATA_CHARGE) <= 0) {
            return "装药为零";
        }
        if (locked()) {
            return "锁定 " + Math.round(lockDistance());
        }
        return "待发";
    }

    private int statusColor() {
        if (menu.getData(CannonMenu.DATA_COOLDOWN) > 0) {
            return TEXT_WARN;
        }
        if (!hasPowder()) {
            return TEXT_WARN;
        }
        if (menu.getData(CannonMenu.DATA_CHARGE) <= 0) {
            return TEXT_GOLD;
        }
        if (locked()) {
            return TEXT_GOLD;
        }
        return TEXT_GOOD;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // header
        graphics.drawString(font, TITLE, 9, 5, TEXT, false);
        int lamp = readyToFire() ? 0xFF49C46A : (menu.getData(CannonMenu.DATA_COOLDOWN) > 0 ? 0xFFD9A441 : 0xFFD94A3A);
        graphics.fill(159, 6, 165, 12, 0xFF06070A);
        graphics.fill(160, 7, 164, 11, lamp);

        // equipment labels
        drawSlotLabel(graphics, "火药", 31, 0xFFD8B25A);
        drawSlotLabel(graphics, "填充", 59, 0xFF9B978D);
        drawSlotLabel(graphics, "货物", 87, 0xFFE6705A);

        // status
        int color = statusColor();
        graphics.fill(SLIDER_X, STATUS_Y + 2, SLIDER_X + 4, STATUS_Y + 6, color);
        graphics.drawString(font, statusText(), SLIDER_X + 8, STATUS_Y, color, false);

        // range / flight, kept compact enough for the 44 px left column
        graphics.drawString(font, "R", 10, 126, TEXT_GOLD, false);
        graphics.drawString(font, Integer.toString(menu.getData(CannonMenu.DATA_RANGE)), 18, 126, TEXT_GOOD, false);
        graphics.drawString(font, "T", 10, 136, TEXT_GOLD, false);
        graphics.drawString(font, String.format("%.0fs", menu.getData(CannonMenu.DATA_FLIGHT) / 20.0),
                18, 136, TEXT_DIM, false);

        // keyboard hint; the charge keys are also in the slider tooltip
        graphics.drawString(font, "↑↓←→ 微调  Shift ×5", SLIDER_X, HINT_Y, TEXT_MUTED, false);

        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT_DIM, false);
    }

    private void drawSlotLabel(GuiGraphics graphics, String label, int y, int dotColor) {
        graphics.fill(10, y + 4, 13, y + 7, dotColor);
        graphics.drawString(font, label, 15, y, TEXT, false);
    }

    /** 自绘滑条：14 px 高，上面是标签/数值，下面是细轨和铜制滑块。 */
    private static final class AimSlider extends AbstractSliderButton {
        private final String label;
        private final String unit;
        private final int min;
        private final int max;
        private final IntConsumer onChanged;
        private int lastSent;

        AimSlider(int x, int y, int width, int height, String label, String unit, int min, int max,
                  int initial, IntConsumer onChanged) {
            super(x, y, width, height, Component.empty(), ratio(initial, min, max));
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

        private String valueLabel() {
            if (max == CannonBlockEntity.MAX_CHARGE) {
                return intValue() + " / " + max;
            }
            return intValue() + unit;
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
            if (label == null) {
                setMessage(Component.empty());
                return;
            }
            setMessage(Component.literal(label + " " + valueLabel()));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hover = this.active && isMouseOver(mouseX, mouseY);

            graphics.fill(x, y, x + w, y + h, 0xF016171B);
            graphics.fill(x, y, x + w, y + 1, hover ? 0xFFC9A24A : 0xFF46474F);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFF07080A);
            graphics.fill(x, y, x + 1, y + h, 0xFF303139);
            graphics.fill(x + w - 1, y, x + w, y + h, 0xFF07080A);

            graphics.drawString(font, label, x + 4, y + 1, TEXT, false);
            String valueText = valueLabel();
            graphics.drawString(font, valueText, x + w - 4 - font.width(valueText), y + 1,
                    hover ? 0xFFFFE0A0 : TEXT_GOLD, false);

            int trackX = x + 4;
            int trackY = y + h - 3;
            int trackW = w - 8;
            graphics.fill(trackX, trackY, trackX + trackW, trackY + 2, 0xFF08090B);
            graphics.fill(trackX, trackY, trackX + trackW, trackY + 1, 0xFF2C2D34);
            int filled = (int) Math.round(this.value * trackW);
            graphics.fill(trackX, trackY, trackX + filled, trackY + 2, 0xFF8C2B22);

            for (int i = 0; i <= 4; i++) {
                int tx = trackX + (int) Math.round(trackW * i / 4.0);
                graphics.fill(tx, trackY - 1, tx + 1, trackY + 1, 0xFF4A4B54);
            }

            int knobX = trackX + (int) Math.round(this.value * trackW);
            graphics.fill(knobX - 2, y + 9, knobX + 3, y + h, 0xFF07080A);
            graphics.fill(knobX - 1, y + 9, knobX + 2, y + h - 1, hover ? 0xFFE6C76A : 0xFFC9A24A);
            graphics.fill(knobX, y + 10, knobX + 1, y + h - 2, 0xFF5C441D);
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

    /** 黑铁按钮：比原版灰色按钮更贴合界面，也支持禁用/悬停状态。 */
    private static final class SteelButton extends Button {
        private enum Style { NORMAL, FIRE, TOGGLE }

        private final Style style;

        SteelButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hover = this.active && isMouseOver(mouseX, mouseY);
            int bg;
            int border;
            int text;
            if (style == Style.FIRE) {
                bg = !this.active ? 0xFF221A18 : (hover ? 0xFF6B1B18 : 0xFF4A1312);
                border = !this.active ? 0xFF5A4A36 : 0xFFC9A24A;
                text = !this.active ? 0xFF8C8172 : 0xFFFFE3C7;
            } else {
                bg = !this.active ? 0xFF1D1E22 : (hover ? 0xFF3A3D47 : 0xFF2B2D35);
                border = !this.active ? 0xFF44413A : (hover ? 0xFFD8B25A : 0xFF6A5A3A);
                text = !this.active ? 0xFF77736A : 0xFFE2D8C3;
            }

            graphics.fill(x, y, x + w, y + h, bg);
            graphics.fill(x, y, x + w, y + 1, border);
            graphics.fill(x, y, x + 1, y + h, border);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFF07080A);
            graphics.fill(x + w - 1, y, x + w, y + h, 0xFF07080A);

            if (style == Style.FIRE) {
                graphics.fill(x + 2, y + 2, x + w - 2, y + 3, hover ? 0xFFB3261E : 0xFF7A1B17);
                graphics.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, 0xFF2B0B0A);
            } else if (hover) {
                graphics.fill(x + 1, y + 1, x + 2, y + h - 1, 0x33FFE0A0);
            }

            Font font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, getMessage(), x + w / 2, y + (h - 8) / 2, text);
        }
    }
}
