package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.block.CannonGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 把方块实体里的俯仰/方位角画成一台会转、会后坐、会喷火的炮。 */
public class CannonRenderer implements BlockEntityRenderer<CannonBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CannonWorks.MOD_ID, "textures/entity/cannon.png");

    private final ModelPart base;
    private final ModelPart barrel;
    /** 每门炮上一次已经触发过炮口特效的开火时间，避免渲染帧重复生成。 */
    private final Map<BlockPos, Long> flashedAt = new HashMap<>();

    public CannonRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(CannonModel.LAYER);
        this.base = root.getChild("base");
        this.barrel = root.getChild("barrel");
    }

    @Override
    public void render(CannonBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float pitch = (float) Math.toRadians(blockEntity.getPitch());
        float recoil = recoilUnits(blockEntity, partialTick);
        float scale = CannonModel.MODEL_SCALE;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blockEntity.getYaw()));

        // 底座不后坐；模型按共享放大倍数绘制。
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        base.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();

        // 沿炮膛轴线回弹。位移在世界方向计算，再进入放大后的炮管坐标。
        if (recoil > 0.001f) {
            float recoilWorld = (float) CannonGeometry.world(recoil);
            poseStack.translate(0.0,
                    -recoilWorld * Math.sin(pitch),
                    -recoilWorld * Math.cos(pitch));
        }

        poseStack.scale(scale, scale, scale);
        barrel.xRot = -pitch;
        barrel.render(poseStack, consumer, packedLight, packedOverlay);

        poseStack.popPose();

        triggerMuzzleFlash(blockEntity);
    }

    /** 由方块实体的开火时间驱动，不使用客户端随机数。 */
    private static float recoilUnits(CannonBlockEntity blockEntity, float partialTick) {
        long fired = blockEntity.getFireGameTime();
        Level level = blockEntity.getLevel();
        if (fired < 0L || level == null) {
            return 0.0f;
        }
        float age = level.getGameTime() - fired + partialTick;
        if (age < 0.0f || age >= CannonGeometry.RECOIL_TICKS) {
            return 0.0f;
        }
        return (float) (Math.sin(age / CannonGeometry.RECOIL_TICKS * Math.PI)
                * CannonGeometry.RECOIL_MODEL_UNITS);
    }

    /**
     * 开火后 1 tick 内触发屏幕震动。真正的大量烟/火由服务端在
     * {@link CannonBlockEntity#fire} 里用 sendParticles 广播，所有附近玩家都能看到。
     */
    private void triggerMuzzleFlash(CannonBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide) {
            return;
        }
        long fired = blockEntity.getFireGameTime();
        if (fired < 0L) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        Long previous = flashedAt.get(pos);
        long age = level.getGameTime() - fired;
        if (age < 0L || age > 1L) {
            if (previous != null) {
                flashedAt.remove(pos);
            }
            return;
        }
        if (previous != null && previous == fired) {
            return;
        }
        flashedAt.put(pos, fired);

        Vec3 muzzle = CannonGeometry.muzzle(pos, blockEntity.getYaw(), blockEntity.getPitch());
        float strength = 1.7f;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            double distance = Math.sqrt(player.distanceToSqr(muzzle));
            strength = Mth.clamp(1.8f - (float) distance * 0.075f, 0.35f, 1.8f);
        }
        CannonShake.trigger(strength);
    }
}
