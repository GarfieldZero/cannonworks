package com.cannonworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 大炮几何的共享常量与坐标换算。
 *
 * <p>模型本身仍然以 1/16 方块为单位、在 {@code CannonModel} 里以 1× 尺度建模；
 * 渲染时整体放大 {@link #MODEL_SCALE} 倍，因此视觉尺寸达到多格，但服务端的
 * 炮口位置、粒子和弹道起点必须用同一套换算，不能各写一份。</p>
 */
public final class CannonGeometry {

    /** 模型放大倍数：3 单位口径 → 0.75 格；24 单位炮管 → 6 格。 */
    public static final float MODEL_SCALE = 4.0f;

    /** 炮管枢轴（炮尾）高度，模型单位。 */
    public static final float PIVOT_Y = 6.9f;
    /** 炮管枢轴 Z 偏移，模型单位。 */
    public static final float PIVOT_Z = -2.6f;
    /** 炮口端面到枢轴的距离，模型单位。 */
    public static final float MUZZLE_Z = 24.0f;

    /** 后坐距离（模型单位）；放大后约 1.17 格。 */
    public static final float RECOIL_MODEL_UNITS = 3.5f;
    /** 后坐持续 tick。 */
    public static final int RECOIL_TICKS = 10;

    private CannonGeometry() {
    }

    /** 水平朝向：与 BE 的 yaw 约定一致，0° = +Z。 */
    public static Vec3 horizontal(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** 炮膛方向（抬头为正 pitch）。 */
    public static Vec3 direction(float yawDegrees, float pitchDegrees) {
        Vec3 horizontal = horizontal(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec3(horizontal.x * Math.cos(pitch), Math.sin(pitch),
                horizontal.z * Math.cos(pitch)).normalize();
    }

    /** 方块中心 + 炮尾枢轴。 */
    public static Vec3 pivot(BlockPos pos, float yawDegrees) {
        return Vec3.atCenterOf(pos)
                .add(0.0, world(PIVOT_Y), 0.0)
                .add(horizontal(yawDegrees).scale(world(PIVOT_Z)));
    }

    /** 世界坐标里的炮口位置。 */
    public static Vec3 muzzle(BlockPos pos, float yawDegrees, float pitchDegrees) {
        return pivot(pos, yawDegrees).add(direction(yawDegrees, pitchDegrees).scale(world(MUZZLE_Z)));
    }

    /** 模型单位 → 方块单位。 */
    public static double world(float modelUnits) {
        return modelUnits * MODEL_SCALE / 16.0;
    }
}
