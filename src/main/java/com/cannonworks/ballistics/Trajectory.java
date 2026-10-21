package com.cannonworks.ballistics;

import net.minecraft.world.phys.Vec3;

/**
 * 一条已经算好的弹道（不可变）。
 *
 * <p>用「水平距离比例 u ∈ [0,1]」参数化，高度是 u 的二次函数：</p>
 * <pre>
 *   h(u) = deltaY * u + 4 * apex * u * (1 - u)
 *   pos(u) = origin + direction * (range * u) + (0, h(u), 0)
 * </pre>
 *
 * <p>为什么不直接用重力积分：真实抛物线里顶点 ≈ 射程·tanθ/4，
 * 打 1000 格 45° 顶点就要 250 格、60° 要 430 格，直接飞出世界高度。
 * 参数化之后射程/顶点/飞行时间三个旋钮互相独立，手感可调。</p>
 *
 * @param origin      炮口位置
 * @param direction   水平单位向量（y 恒为 0）
 * @param range       水平射程（格）
 * @param apex        弹道顶点相对炮口的高度（格）
 * @param deltaY      落点相对炮口的高差（格）
 * @param flightTicks 总飞行时间（tick）
 */
public record Trajectory(Vec3 origin, Vec3 direction, double range, double apex, double deltaY, int flightTicks) {

    public Trajectory {
        if (flightTicks < 1) {
            throw new IllegalArgumentException("flightTicks must be >= 1, got " + flightTicks);
        }
    }

    /** u ∈ [0,1] 处的世界坐标。 */
    public Vec3 sample(double u) {
        double clamped = Math.max(0.0, Math.min(1.0, u));
        double height = deltaY * clamped + 4.0 * apex * clamped * (1.0 - clamped);
        return origin.add(direction.scale(range * clamped)).add(0.0, height, 0.0);
    }

    /** 第 tick 个游戏刻应该在的位置。 */
    public Vec3 sampleAtTick(int tick) {
        return sample((double) tick / flightTicks);
    }

    public Vec3 end() {
        return sample(1.0);
    }

    /** 落点水平方向（单位向量），用于弹坑朝向、着陆提示。 */
    public Vec3 horizontalDirection() {
        return direction;
    }

    /** 该弹道是否属于「高抛」（顶点超过 120 格）。 */
    public boolean isHighArc() {
        return apex >= 120.0;
    }
}
