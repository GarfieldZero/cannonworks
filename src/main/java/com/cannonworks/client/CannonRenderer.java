package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 把方块实体里的俯仰/方位角画成一台会转的炮。 */
public class CannonRenderer implements BlockEntityRenderer<CannonBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CannonWorks.MOD_ID, "textures/entity/cannon.png");

    private final ModelPart base;
    private final ModelPart barrel;

    public CannonRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(CannonModel.LAYER);
        this.base = root.getChild("base");
        this.barrel = root.getChild("barrel");
    }

    @Override
    public void render(CannonBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blockEntity.getYaw()));

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        base.render(poseStack, consumer, packedLight, packedOverlay);

        // 炮管绕支点俯仰：+Z 方向抬头 = 负 xRot
        barrel.xRot = -(float) Math.toRadians(blockEntity.getPitch());
        barrel.render(poseStack, consumer, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
