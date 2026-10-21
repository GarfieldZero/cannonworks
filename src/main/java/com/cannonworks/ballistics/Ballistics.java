package com.cannonworks.ballistics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 弹道公式与参数表。全部为纯函数，方便后续做单元测试和给火控计算机反解。
 */
public final class Ballistics {

    /** 俯仰角下限（度）。 */
    public static final double MIN_PITCH = 5.0;
    /** 俯仰角上限（度）。 */
    public static final double MAX_PITCH = 85.0;
    /** 平均飞行速度（格/秒）。1000 格 ≈ 20 秒。 */
    public static final double BASE_SPEED = 50.0;
    /** 顶点高度下限/上限（格）。 */
    public static final double MIN_APEX = 10.0;
    public static final double MAX_APEX = 300.0;
    /** 高抛判定线（格）。 */
    public static final double HIGH_ARC_APEX = 120.0;

    private Ballistics() {
    }

    /**
     * 顶点高度：沿用真实抛物线里 apex/range = tanθ/4 的比例，但夹在 [10, 300]。
     * 低抛 15° 打 1000 格 → 67 格（贴地，容易被山挡）；高抛 60° → 300 格（被夹住，能越山）。
     */
    public static double apexFor(double range, double pitchDeg) {
        double clamped = Mth.clamp(pitchDeg, MIN_PITCH, MAX_PITCH);
        double apex = range * Math.tan(Math.toRadians(clamped)) / 4.0;
        return Mth.clamp(apex, MIN_APEX, MAX_APEX);
    }

    /**
     * 飞行时间：距离决定基础时间，俯仰角让高抛更慢（抛物线更长）。
     * 1000 格 45° → 20 × 1.35 ≈ 27 秒。
     */
    public static int flightTicksFor(double range, double pitchDeg) {
        double clamped = Mth.clamp(pitchDeg, MIN_PITCH, MAX_PITCH);
        double seconds = range / BASE_SPEED * (1.0 + 0.5 * Math.sin(Math.toRadians(clamped)));
        return Math.max(20, (int) Math.round(seconds * 20.0));
    }

    /**
     * 由炮口与目标点构造弹道。
     *
     * @param origin   炮口
     * @param target   目标点（决定水平方向、射程与高差）
     * @param pitchDeg 俯仰角
     */
    public static Trajectory create(Vec3 origin, Vec3 target, double pitchDeg) {
        double dx = target.x - origin.x;
        double dz = target.z - origin.z;
        double range = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        Vec3 direction = new Vec3(dx, 0.0, dz).normalize();
        double apex = apexFor(range, pitchDeg);
        int ticks = flightTicksFor(range, pitchDeg);
        return new Trajectory(origin, direction, range, apex, target.y - origin.y, ticks);
    }
}
