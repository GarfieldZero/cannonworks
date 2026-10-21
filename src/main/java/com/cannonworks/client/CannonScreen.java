package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.menu.CannonMenu;
import com.cannonworks.network.CannonControlPacket;
import com.cannonworks.network.CannonNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** 大炮的装填/瞄准界面。 */
public class CannonScreen extends AbstractContainerScreen<CannonMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CannonWorks.MOD_ID, "textures/gui/cannon.png");

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

        addRenderableWidget(Button.builder(Component.literal("点火"),
                        b -> send(0, 0, 0, true))
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
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
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
