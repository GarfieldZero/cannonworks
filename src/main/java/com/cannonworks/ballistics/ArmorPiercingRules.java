package com.cannonworks.ballistics;

/**
 * 穿甲弹的纯规则：硬度门槛、法线反射、什么时候停止跳弹。
 *
 * <p>刻意不引入任何 Minecraft 类型，这样 Testing 不用加载游戏，
 * ShellManager 只负责把方块硬度和命中面法线翻译成这里的数字。</p>
 */
public final class ArmorPiercingRules {

    /** 黑曜石的硬度；「黑曜石及以下」就是 ≤ 这个值。 */
    public static final float OBSIDIAN_HARDNESS = 50.0f;

    /** 跳弹段的重力，单位：格/tick²。约等于原版实体的手感，比真实 9.8m/s² 夸张一点更带劲。 */
    public static final double GRAVITY_PER_TICK = 0.05;

    /** 法向恢复系数：撞基岩后还能剩下多少法向速度。 */
    public static final double DEFAULT_RESTITUTION = 0.55;

    /** 切向摩擦：打水漂时水平方向也会掉速，不会真的永动。 */
    public static final double DEFAULT_FRICTION = 0.85;

    /** 低于这个速度就不再弹，直接在落点结算。 */
    public static final double MIN_BOUNCE_SPEED = 0.08;

    /** 默认最多跳 3 次：够帅，也够收敛。 */
    public static final int DEFAULT_MAX_BOUNCES = 3;

    private ArmorPiercingRules() {
    }

    /**
     * 硬度是否在穿甲范围内。
     *
     * <p>硬度 &lt; 0 的是基岩/屏障/命令方块这类不可破坏方块，永远不穿；
     * 上限包含黑曜石（50）本身，主人在需求里点名的就是「黑曜石及以下」。</p>
     */
    public static boolean canPierce(float hardness, double maxHardness) {
        return hardness >= 0.0f && hardness <= maxHardness;
    }

    /**
     * 该不该结束跳弹。
     *
     * @param bounces    已经弹了几次
     * @param maxBounces 配置上限
     * @param speed      反弹后的速度大小（格/tick）
     * @param minSpeed   低于它就停
     */
    public static boolean shouldStop(int bounces, int maxBounces, double speed, double minSpeed) {
        return bounces >= maxBounces || speed < minSpeed;
    }

    /** 三维速度。用 record 而不是 Vec3，测试类才能不碰 Minecraft。 */
    public record Velocity(double x, double y, double z) {

        public double dot(Velocity other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    /**
     * 镜面反射 + 阻尼。
     *
     * <p>公式：{@code v' = 切向 × 摩擦 + 法向 × (-恢复系数 × v·n)}。
     * 其中切向 = {@code v - (v·n)n}。这样地面（法线朝上）会把向下的速度翻成向上，
     * 墙面（法线水平）会把水平分量反弹，同时保留另一条水平轴作为“打水漂”的前进方向。</p>
     *
     * @param velocity   撞之前的每 tick 速度（不是每秒）
     * @param normal     命中面的单位法线，指向方块外侧
     * @param restitution 法向恢复系数，0~1
     * @param friction    切向摩擦，0~1
     */
    public static Velocity reflect(Velocity velocity, Velocity normal, double restitution, double friction) {
        double normalSpeed = velocity.dot(normal);

        double tangentX = velocity.x - normalSpeed * normal.x;
        double tangentY = velocity.y - normalSpeed * normal.y;
        double tangentZ = velocity.z - normalSpeed * normal.z;

        double reflectX = tangentX * friction - restitution * normalSpeed * normal.x;
        double reflectY = tangentY * friction - restitution * normalSpeed * normal.y;
        double reflectZ = tangentZ * friction - restitution * normalSpeed * normal.z;

        return new Velocity(reflectX, reflectY, reflectZ);
    }
}
