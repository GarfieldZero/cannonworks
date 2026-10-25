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
    /** 顶点高度下限/上限（格）。上限给得足够高，才能打出 10km 级的战略射程：
     *  45° 打 10000 格，真实抛物线顶点就是 2500 格。 */
    public static final double MIN_APEX = 10.0;
    public static final double MAX_APEX = 3000.0;
    /**
     * 安全上限（不是玩法上限）：只为防止极端输入让飞行 tick 溢出、或一发把服务器拖去生成半个世界。
     * 实际能打多远由「装药 × 俯仰效率」决定。
     */
    public static final double MAX_RANGE = 100000.0;
    /** 高抛判定线（格）。 */
    public static final double HIGH_ARC_APEX = 120.0;

    private Ballistics() {
    }

    /**
     * 俯仰角对火药效率的修正系数：45 度为 1，每偏离 1 度变化 1/90。
     *
     * <p>低抛更省药（5 度约 1.44 倍射程），高抛更费药（85 度约 0.56 倍）。
     * 这样「俯仰」不再只改弹道形状，也参与射程计算——高抛拿射程换高度。</p>
     */
    public static double pitchEfficiency(double pitchDeg) {
        double clamped = Mth.clamp(pitchDeg, MIN_PITCH, MAX_PITCH);
        // 余弦曲线：取水平分量占比，并以 45 度为 1。
        // 0 度约 1.41 倍、45 度 1.0、80 度 0.25、接近 90 度趋近 0。
        double value = Math.cos(Math.toRadians(clamped)) / Math.cos(Math.toRadians(45.0));
        return Mth.clamp(value, 0.05, 1.5);
    }

    /**
     * 顶点高度：沿用真实抛物线里 apex/range = tanθ/4 的比例。
     * 15° 打 10000 格 → 670 格；45° → 2500 格；超过 3000 才夹住。
     *
     * <p>MC 的方块只到 y=319，但<b>实体没有高度限制</b>：顶点超过 320 之后那一段既没有方块、
     * 也就没有东西能挡住炮弹——这正是「高抛能越过一切地形」的物理来源。</p>
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
