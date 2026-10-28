package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Ballistics;
import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.network.CannonAimPacket;
import com.cannonworks.network.CannonControlPacket;
import com.cannonworks.network.CannonNetwork;
import com.cannonworks.network.CannonRenameCargoPacket;
import com.cannonworks.payload.FireMode;
import java.util.Locale;
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
 *
 * <p>精确输入：点滑条右侧的数值文字，会在原地浮出一个输入框（Enter 提交、Esc 取消），
 * 这样窄界面里不用为了 0.1 份的精度再挤一行控件。货物槽放石碑时，
 * 最下面那行会从键盘提示换成命名框，省得抱着石碑跑铁砧。</p>
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
    /** 命名框和键盘提示共用这一行：货物是石碑时显示框，否则显示提示。 */
    private static final int NAMING_Y = 131;

    private AimSlider pitchSlider;
    private AimSlider yawSlider;
    private AimSlider chargeSlider;
    private AimSlider activeSlider;
    private SteelButton modeButton;
    private SteelButton solveButton;
    private SteelButton fireButton;
    private EditBox targetXBox;
    private EditBox targetZBox;
    /** 点数值文字时浮出来的手输框，同一时间只服务一行。 */
    private EditBox valueBox;
    private AimSlider editingSlider;
    /** 货物槽里是石碑时才出现；直接改那摞石碑物品的名字。 */
    private EditBox nameBox;
    /** 上次写进命名框的名字，用来判断要不要跟着槽里的物品刷新。 */
    private String lastNameShown;
    /** 刚发出去、还没被服务端回写确认的名字；避免下一 tick 被旧值盖回去。 */
    private String pendingName;
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
                tr("gui.cannonworks.cannon.slider.pitch"), "°", 5, 85, 1,
                menu.getData(CannonMenu.DATA_PITCH), v -> onSlider(ChangeSource.PITCH));
        yawSlider = new AimSlider(sx, topPos + ROW_YAW, SLIDER_WIDTH, SLIDER_HEIGHT,
                tr("gui.cannonworks.cannon.slider.yaw"), "°", 0, 359, 1,
                menu.getData(CannonMenu.DATA_YAW), v -> onSlider(ChangeSource.YAW));
        // 装药滑条走「十分之一份」：范围 0..640，显示一位小数
        chargeSlider = new AimSlider(sx, topPos + ROW_CHARGE, SLIDER_WIDTH, SLIDER_HEIGHT,
                tr("gui.cannonworks.cannon.slider.charge"), "", 0, CannonBlockEntity.MAX_CHARGE,
                CannonBlockEntity.TENTHS_PER_CHARGE, menu.getData(CannonMenu.DATA_CHARGE),
                v -> onSlider(ChangeSource.CHARGE));

        addRenderableWidget(pitchSlider);
        addRenderableWidget(yawSlider);
        addRenderableWidget(chargeSlider);

        // 数值手输框：平时藏着，点滑条右侧的数值文字才浮到那一行上（见 openValueEditor）
        editingSlider = null;
        valueBox = new EditBox(font, 0, 0, 46, 14, Component.translatable("gui.cannonworks.cannon.value.label"));
        valueBox.setMaxLength(6);
        valueBox.setTextColor(0xFFE2D8C3);
        valueBox.setVisible(false);
        addRenderableWidget(valueBox);

        // 命名框：货物槽里放石碑时才露出来。它占底部提示行，但不画原版黑框——
        // 那会把这一行挤成一个突兀的“对话框”；改成面板内嵌输入线的样式。
        nameBox = new EditBox(font, leftPos + SLIDER_X + 4, topPos + NAMING_Y, SLIDER_WIDTH - 8, 12,
                Component.translatable("gui.cannonworks.cannon.name.label"));
        nameBox.setMaxLength(CannonRenameCargoPacket.MAX_NAME_LENGTH);
        nameBox.setHint(Component.translatable("gui.cannonworks.cannon.name.hint"));
        nameBox.setBordered(false);
        nameBox.setTextColor(0xFFE2D8C3);
        nameBox.setVisible(false);
        addRenderableWidget(nameBox);

        // 目标坐标框让出 5px 给「解算」按钮：英文 "Solve" 比中文「解算」宽
        targetXBox = new EditBox(font, leftPos + SLIDER_X, topPos + TARGET_Y, 33, 16,
                Component.literal("X"));
        targetXBox.setMaxLength(9);
        targetXBox.setFilter(text -> text.matches("-?\\d*"));
        targetXBox.setHint(Component.literal("X"));
        targetXBox.setBordered(false);
        targetXBox.setTextColor(0xFFE2D8C3);
        targetXBox.setResponder(text -> updateSolveButton());
        addRenderableWidget(targetXBox);

        targetZBox = new EditBox(font, leftPos + SLIDER_X + 35, topPos + TARGET_Y, 33, 16,
                Component.literal("Z"));
        targetZBox.setMaxLength(9);
        targetZBox.setFilter(text -> text.matches("-?\\d*"));
        targetZBox.setHint(Component.literal("Z"));
        targetZBox.setBordered(false);
        targetZBox.setTextColor(0xFFE2D8C3);
        targetZBox.setResponder(text -> updateSolveButton());
        addRenderableWidget(targetZBox);

        solveButton = new SteelButton(leftPos + SLIDER_X + 70, topPos + TARGET_Y, 30, 16,
                Component.translatable("gui.cannonworks.cannon.button.solve"), b -> solve(),
                SteelButton.Style.NORMAL);
        solveButton.setTooltip(Tooltip.create(Component.translatable("gui.cannonworks.cannon.tooltip.solve")));
        addRenderableWidget(solveButton);

        fireButton = new SteelButton(leftPos + SLIDER_X, topPos + FIRE_Y, SLIDER_WIDTH, 22,
                Component.translatable("gui.cannonworks.cannon.button.fire"), b -> fire(), SteelButton.Style.FIRE);
        addRenderableWidget(fireButton);

        // 比中文「引信 接触」宽一点，英文 "Airburst" 也要放得下
        modeButton = new SteelButton(leftPos + 8, topPos + 104, 50, 16,
                modeLabel(), b -> toggleMode(), SteelButton.Style.TOGGLE);
        modeButton.setTooltip(Tooltip.create(Component.translatable("gui.cannonworks.cannon.tooltip.fuse")));
        addRenderableWidget(modeButton);

        pitchSlider.setTooltip(Tooltip.create(Component.translatable("gui.cannonworks.cannon.tooltip.pitch")));
        yawSlider.setTooltip(Tooltip.create(Component.translatable("gui.cannonworks.cannon.tooltip.yaw")));
        chargeSlider.setTooltip(Tooltip.create(Component.translatable("gui.cannonworks.cannon.tooltip.charge")));

        updateButtons();
    }

    /** 取可读文本：滑条标签是自绘字符串，拿不到 Component 的渲染重载，只能先转成当前语言。 */
    private static String tr(String key) {
        return Component.translatable(key).getString();
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
        // 装药按「十分之一份」向上取整：多装 0.1 份总比差一格打不到强
        int charge = (int) Math.ceil(range / (CannonBlockEntity.RANGE_PER_CHARGE * efficiency)
                * CannonBlockEntity.TENTHS_PER_CHARGE);
        if (charge > CannonBlockEntity.MAX_CHARGE) {
            charge = CannonBlockEntity.MAX_CHARGE;
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("gui.cannonworks.cannon.status.out_of_range"), true);
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
        int charge = (int) Math.ceil(distance / (CannonBlockEntity.RANGE_PER_CHARGE * efficiency)
                * CannonBlockEntity.TENTHS_PER_CHARGE);
        chargeSlider.setLocal(Math.max(0, Math.min(CannonBlockEntity.MAX_CHARGE, charge)));
    }

    /** Keep the locked distance: recompute pitch from the new charge. */
    private void applyPitchForLock() {
        int charge = chargeSlider.intValue();
        if (charge <= 0) {
            return;
        }
        double distance = lockDistance();
        // charge 是十分之一份，先换回「份」再按每份射程反解俯仰
        double charges = charge / (double) CannonBlockEntity.TENTHS_PER_CHARGE;
        double efficiency = distance / (CannonBlockEntity.RANGE_PER_CHARGE * charges);
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
        return Component.translatable(currentMode().translationKey());
    }

    private void toggleMode() {
        FireMode next = currentMode().next();
        CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(
                menu.getBlockEntity().getBlockPos(),
                pitchSlider.intValue(), yawSlider.intValue(), chargeSlider.intValue(), next));
        holdSync = 6;
    }

    private void fire() {
        // 手快直接点「点火」时，先把命名框里没提交的字发出去（包按顺序处理，改名先于开火）
        commitNameBox();
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

    /**
     * 键盘微调：角度 1° 步进（Shift 5°），装药一次 1.0 份（{@code TENTHS_PER_CHARGE}，Shift 5.0 份）。
     *
     * <p>装药 delta 和字段同单位（十分之一份），所以按键那边乘过 10 再送进来。</p>
     */
    private void nudge(int pitchDelta, int yawDelta, int chargeDelta) {
        CannonNetwork.CHANNEL.sendToServer(new CannonControlPacket(
                menu.getBlockEntity().getBlockPos(), pitchDelta, yawDelta, chargeDelta, false));
        pitchSlider.setLocal(pitchSlider.intValue() + pitchDelta);
        yawSlider.setLocal(Math.floorMod(yawSlider.intValue() + yawDelta, 360));
        chargeSlider.setLocal(chargeSlider.intValue() + chargeDelta);
        holdSync = 4;
        updateButtons();
    }

    // ---- 数值手输：点数值文字，在原地浮出输入框（界面太窄，不另开一行） ----

    /** 点中数值文字时调用：把输入框挪到这一行、填上当前值并抢焦点。 */
    private void openValueEditor(AimSlider slider) {
        commitValueEditor(true);
        editingSlider = slider;
        slider.hideValue = true;
        valueBox.setX(slider.getX() + slider.getWidth() - 46);
        valueBox.setY(slider.getY());
        valueBox.setWidth(46);
        valueBox.setHeight(slider.getHeight());
        valueBox.setValue(slider.editableText());
        valueBox.setCursorPosition(valueBox.getValue().length());
        valueBox.setVisible(true);
        setFocused(valueBox);
        valueBox.setFocused(true);
    }

    /**
     * 收起手输框。{@code commit} 为 true 时解析并提交，解析不了就当没改过。
     *
     * <p>手输的值就是最终值，所以顺手解掉「锁定距离」——不然解算逻辑会拿新装药反推俯仰、
     * 把玩家刚打的数字改掉，「我明明输了它又变了」最招人烦。</p>
     */
    private void commitValueEditor(boolean commit) {
        if (editingSlider == null) {
            return;
        }
        AimSlider slider = editingSlider;
        if (commit) {
            Double typed = NumericInput.parse(valueBox.getValue());
            if (typed != null) {
                slider.setLocal(slider.fromTyped(typed));
                lockX = Integer.MIN_VALUE;
                lockZ = Integer.MIN_VALUE;
                CannonNetwork.CHANNEL.sendToServer(new CannonAimPacket(
                        menu.getBlockEntity().getBlockPos(),
                        pitchSlider.intValue(), yawSlider.intValue(), chargeSlider.intValue(), currentMode()));
                holdSync = 6;
                updateButtons();
            }
        }
        slider.hideValue = false;
        editingSlider = null;
        valueBox.setVisible(false);
        valueBox.setFocused(false);
        setFocused(null);
    }

    // ---- 滑条：自己接管点击与拖拽（容器界面不会把 drag 转发给控件） ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 点命名框以外的地方 = 「这名字我确认了」，别让它随着焦点丢失悄悄消失
            if (nameBox != null && nameBox.isVisible() && nameBox.isFocused()
                    && !nameBox.isMouseOver(mouseX, mouseY)) {
                commitNameBox();
            }
            if (valueBox != null && valueBox.isVisible()) {
                if (valueBox.isMouseOver(mouseX, mouseY)) {
                    setFocused(valueBox);
                    valueBox.setFocused(true);
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                // 点框外 = 确认，然后继续走正常点击（可能是拖另一条滑条）
                commitValueEditor(true);
            }
            for (GuiEventListener child : children()) {
                if (child instanceof AimSlider slider && slider.visible && slider.isMouseOver(mouseX, mouseY)) {
                    if (slider.isOverValueText(mouseX, mouseY)) {
                        openValueEditor(slider);
                        slider.playDownSound(Minecraft.getInstance().getSoundManager());
                        return true;
                    }
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
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        EditBox focusedBox = getFocused() instanceof EditBox box ? box : null;

        // 手输框：Enter 提交、Esc 取消，其余按键先给它自己，再整个吞掉
        if (focusedBox != null && focusedBox == valueBox && valueBox.isVisible()) {
            if (enter) {
                commitValueEditor(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                commitValueEditor(false);
                return true;
            }
            return swallowForTextBox(valueBox, keyCode, scanCode, modifiers);
        }
        // 命名框：Enter 提交；提交逻辑见 commitNameBox（失焦/关界面/点火也会走同一条路）
        if (focusedBox != null && focusedBox == nameBox && nameBox.isVisible()) {
            if (enter) {
                commitNameBox();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            return swallowForTextBox(nameBox, keyCode, scanCode, modifiers);
        }
        // 目标 X/Z 这些输入框也一样：别让按键穿到容器逻辑去
        if (focusedBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // Esc 仍然要能关界面，这是原版习惯
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            return swallowForTextBox(focusedBox, keyCode, scanCode, modifiers);
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
                nudge(0, 0, step * CannonBlockEntity.TENTHS_PER_CHARGE);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                nudge(0, 0, -step * CannonBlockEntity.TENTHS_PER_CHARGE);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    /**
     * 输入框有焦点时，把按键截在这里。
     *
     * <p>必须这么做的原因：{@code AbstractContainerScreen#keyPressed} 在把按键交给焦点控件之后，
     * 还会检查背包键（E）、快捷栏数字键和拾取键。输入框对字母键返回 false，
     * 于是「e」会被当成「关闭界面」——名字里带 e 的字根本打不进去；数字键还会顺手点快捷栏。</p>
     *
     * <p>原版铁砧界面（{@code AnvilScreen}）就是
     * {@code EditBox#keyPressed() || EditBox#canConsumeInput()} 这么吞的，这里照抄同一套，
     * 行为才和玩家习惯一致。</p>
     */
    private static boolean swallowForTextBox(EditBox box, int keyCode, int scanCode, int modifiers) {
        // 先让输入框处理自己认识的键（方向键、退格、Ctrl+C/V 等）
        if (box.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // 剩下的字母/数字键：文字由 charTyped 送进输入框，这里只要报「我吃下了」，
        // 后面的背包键/快捷栏逻辑就不会再看到这次按键。
        return box.canConsumeInput();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 命名框的显隐和内容不能受 holdSync 影响，否则拖完滑条它会晚几 tick 才出现
        syncNameBox();
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

    /**
     * 命名框只在货物槽是石碑时出现；平时那一行还是键盘提示。
     *
     * <p>名字从槽里的物品回读，这样在界面外用铁砧改过的名字也能显示；
     * 正在输入时不回写，免得把玩家打的字覆盖掉。</p>
     */
    private void syncNameBox() {
        if (nameBox == null) {
            return;
        }
        ItemStack cargo = menu.getSlot(CannonBlockEntity.SLOT_CARGO).getItem();
        boolean waystone = CannonBlockEntity.isWaystoneItem(cargo);
        nameBox.setVisible(waystone);
        if (!waystone) {
            // 槽里的石碑被拿走时要收回焦点，否则按键还会打进一个看不见的框
            if (nameBox.isFocused()) {
                nameBox.setFocused(false);
                setFocused(null);
            }
            lastNameShown = null;
            pendingName = null;
            return;
        }
        if (nameBox.isFocused()) {
            return;
        }
        String current = cargo.hasCustomHoverName() ? cargo.getHoverName().getString() : "";
        if (pendingName != null) {
            if (!current.equals(pendingName)) {
                // 服务端还没把新名字同步回来，别用旧值把玩家刚提交的字盖掉
                return;
            }
            pendingName = null;
        }
        if (!current.equals(lastNameShown)) {
            nameBox.setValue(current);
            lastNameShown = current;
        }
    }

    /**
     * 把命名框里的字发给服务端。
     *
     * <p>不能只认回车：玩家打完名字直接点「点火」时，点按钮会把焦点抢走，
     * 以前这次输入就丢了——看起来就像「明明命名了却没生效」。
     * 现在失焦、关界面、点火都会先走这里；下次同步回来前用 {@link #pendingName} 挡住旧值。</p>
     */
    private void commitNameBox() {
        if (nameBox == null || !nameBox.isVisible() || !nameBox.isFocused()) {
            return;
        }
        String typed = nameBox.getValue().trim();
        nameBox.setFocused(false);
        setFocused(null);
        pendingName = typed;
        CannonNetwork.CHANNEL.sendToServer(new CannonRenameCargoPacket(
                menu.getBlockEntity().getBlockPos(), typed));
    }

    @Override
    public void onClose() {
        // 关界面也算「确认」，别让刚打的字随着界面一起消失
        commitNameBox();
        super.onClose();
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

    private Component statusText() {
        if (menu.getData(CannonMenu.DATA_COOLDOWN) > 0) {
            return Component.translatable("gui.cannonworks.cannon.status.reloading",
                    String.format(Locale.ROOT, "%.1f", menu.getData(CannonMenu.DATA_COOLDOWN) / 20.0));
        }
        if (!hasPowder()) {
            return Component.translatable("gui.cannonworks.cannon.status.no_powder");
        }
        if (menu.getData(CannonMenu.DATA_CHARGE) <= 0) {
            return Component.translatable("gui.cannonworks.cannon.status.zero_charge");
        }
        if (locked()) {
            return Component.translatable("gui.cannonworks.cannon.status.locked", Math.round(lockDistance()));
        }
        return Component.translatable("gui.cannonworks.cannon.status.ready");
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
        drawSlotLabel(graphics, Component.translatable("gui.cannonworks.cannon.slot.propellant"), 31, 0xFFD8B25A);
        drawSlotLabel(graphics, Component.translatable("gui.cannonworks.cannon.slot.filler"), 59, 0xFF9B978D);
        drawSlotLabel(graphics, Component.translatable("gui.cannonworks.cannon.slot.cargo"), 87, 0xFFE6705A);

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

        // 键盘提示和命名框抢同一行：石碑在槽里时让位给命名框
        if (nameBox == null || !nameBox.isVisible()) {
            graphics.drawString(font, Component.translatable("gui.cannonworks.cannon.hint"),
                    SLIDER_X, HINT_Y, TEXT_MUTED, false);
        } else {
            // 输入框本身是无边框的；这里补一条内嵌底槽，避免直接飘在背景上。
            graphics.fill(SLIDER_X - 2, NAMING_Y - 2, SLIDER_X + SLIDER_WIDTH + 2, NAMING_Y + 13, 0xCC0B0C10);
            graphics.fill(SLIDER_X - 2, NAMING_Y + 13, SLIDER_X + SLIDER_WIDTH + 2, NAMING_Y + 14, 0x66D8B25A);
        }

        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT_DIM, false);
    }

    private void drawSlotLabel(GuiGraphics graphics, Component label, int y, int dotColor) {
        graphics.fill(10, y + 4, 13, y + 7, dotColor);
        graphics.drawString(font, label, 15, y, TEXT, false);
    }

    /** 自绘滑条：14 px 高，上面是标签/数值，下面是细轨和铜制滑块。 */
    private static final class AimSlider extends AbstractSliderButton {
        private final String label;
        private final String unit;
        private final int min;
        private final int max;
        /** 显示换算：1 = 直接显示整数（角度），10 = 一位小数（装药，内部单位是十分之一份）。 */
        private final int displayDivisor;
        private final IntConsumer onChanged;
        private int lastSent;
        /** 手输框浮在上面时先别画数值，免得字叠字。 */
        private boolean hideValue;

        AimSlider(int x, int y, int width, int height, String label, String unit, int min, int max,
                  int displayDivisor, int initial, IntConsumer onChanged) {
            super(x, y, width, height, Component.empty(), ratio(initial, min, max));
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.displayDivisor = displayDivisor;
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
            if (displayDivisor == CannonBlockEntity.TENTHS_PER_CHARGE) {
                // 装药：内部 80 显示成「8.0 / 64.0」，拖动时连续变化，不会一顿一顿跳整数份
                return String.format(Locale.ROOT, "%.1f / %.1f",
                        intValue() / (double) displayDivisor, max / (double) displayDivisor);
            }
            return intValue() + unit;
        }

        /** 鼠标是否落在数值文字上——只有这块区域响应「点开手输」，其余部分照常拖动。 */
        boolean isOverValueText(double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            String text = valueLabel();
            int textX = getX() + getWidth() - 4 - font.width(text);
            int textY = getY() + 1;
            return mouseX >= textX - 1 && mouseX <= getX() + getWidth() - 3
                    && mouseY >= textY - 1 && mouseY <= textY + 9;
        }

        /** 手输框里显示的文本：角度是整数，装药是一位小数。 */
        String editableText() {
            if (displayDivisor == CannonBlockEntity.TENTHS_PER_CHARGE) {
                return String.format(Locale.ROOT, "%.1f", intValue() / (double) displayDivisor);
            }
            return Integer.toString(intValue());
        }

        /** 把玩家输入的「人话数值」换回内部单位并夹住范围；方位角绕圈而不是夹死。 */
        int fromTyped(double humanValue) {
            if (min == 0 && max == 359) {
                return Math.floorMod((int) Math.round(humanValue), 360);
            }
            return clamp((int) Math.round(humanValue * displayDivisor));
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
            if (!hideValue) {
                String valueText = valueLabel();
                graphics.drawString(font, valueText, x + w - 4 - font.width(valueText), y + 1,
                        hover ? 0xFFFFE0A0 : TEXT_GOLD, false);
            }

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
