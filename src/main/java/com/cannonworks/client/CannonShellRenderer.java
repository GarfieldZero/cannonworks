package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonGeometry;
import com.cannonworks.entity.CannonShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 炮弹实体：黑铁弹体、黄铜箍、风帽、尾翼，带旋转和尾焰拖尾。 */
public class CannonShellRenderer extends EntityRenderer<CannonShellEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CannonWorks.MOD_ID, "textures/entity/cannon_shell.png");

    private final ModelPart shell;
    private final Map<Integer, Long> lastTrailTick = new HashMap<>();

    public CannonShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shell = context.bakeLayer(CannonShellModel.LAYER);
    }

    @Override
    public void render(CannonShellEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getFlightYaw()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getFlightPitch()));
        // 线膛炮的滚转；只影响外观。
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.tickCount * 27L) % 360L));

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        shell.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        spawnTrail(entity);
    }

    /** 每 2 游戏 tick 在弹尾补一小簇火光和烟，不依赖客户端随机数。 */
    private void spawnTrail(CannonShellEntity entity) {
        Level level = entity.level();
        if (!level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        if ((now & 1L) != 0L) {
            return;
        }
        int id = entity.getId();
        Long previous = lastTrailTick.get(id);
        if (previous != null && previous == now) {
            return;
        }
        lastTrailTick.put(id, now);
        if (lastTrailTick.size() > 128) {
            lastTrailTick.clear();
        }

        double yaw = Math.toRadians(entity.getFlightYaw());
        float pitch = (float) Math.toRadians(entity.getFlightPitch());
        Vec3 dir = CannonGeometry.direction(entity.getFlightYaw(), entity.getFlightPitch());
        Vec3 side = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3 up = side.cross(dir).normalize();
        Vec3 tail = entity.position().subtract(dir.scale(0.78));
        double phase = (now % 12L) / 12.0 * Math.PI * 2.0;

        Vec3 flameAt = tail.add(side.scale(Math.cos(phase) * 0.06))
                .add(up.scale(Math.sin(phase) * 0.06));
        Vec3 flameVelocity = dir.scale(-0.10).add(side.scale(Math.cos(phase) * 0.03));
        level.addParticle(ParticleTypes.FLAME, flameAt.x, flameAt.y, flameAt.z,
                flameVelocity.x, flameVelocity.y, flameVelocity.z);

        Vec3 smokeAt = tail.subtract(dir.scale(0.12));
        level.addParticle(ParticleTypes.SMOKE, smokeAt.x, smokeAt.y, smokeAt.z,
                -dir.x * 0.04, -dir.y * 0.04 + 0.01, -dir.z * 0.04);

        if (now % 4L == 0L) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    tail.x, tail.y, tail.z, -dir.x * 0.04, 0.015, -dir.z * 0.04);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CannonShellEntity entity) {
        return TEXTURE;
    }
}
