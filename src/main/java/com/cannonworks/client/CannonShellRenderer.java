package com.cannonworks.client;

import com.cannonworks.entity.CannonShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * M0 占位渲染：一颗被放大的铁块。
 * M1 之后换成真正的炮弹模型（带尾焰、旋转、拖尾粒子）。
 */
public class CannonShellRenderer extends EntityRenderer<CannonShellEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");

    private final BlockRenderDispatcher blockRenderer;

    public CannonShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(CannonShellEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.3, 0.0);
        poseStack.scale(0.8f, 0.8f, 0.8f);
        blockRenderer.renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(), poseStack, bufferSource,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CannonShellEntity entity) {
        return TEXTURE;
    }
}
