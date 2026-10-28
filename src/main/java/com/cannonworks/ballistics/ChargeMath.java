package com.cannonworks.ballistics;

/**
 * 装药的整数规则（纯函数，不引用任何 Minecraft 类型）。
 *
 * <p>内部单位是「十分之一份」：80 = 8.0 份。这些规则——向上取整扣火药、
 * 火药不够时封顶、旧存档 ×10 迁移——最容易在后续改动里悄悄坏掉，
 * 而 {@code CannonBlockEntity} 需要完整 Minecraft 环境才能构造，
 * 所以把判断抽到这里，交给普通 JUnit 单元测试守住。</p>
 */
public final class ChargeMath {

    /** 1.0 份装药 = 10 个内部单位。 */
    public static final int TENTHS_PER_CHARGE = 10;
    /** 单发上限：640 = 64.0 份 = 一组火药。 */
    public static final int MAX_CHARGE_TENTHS = 640;
    /** 旧存档（整数份）的装药上限，用来判断「没有单位记号的值是不是旧格式」。 */
    public static final int LEGACY_MAX_CHARGE = 64;

    private ChargeMath() {
    }

    /**
     * 打一发需要几个火药：8.1 份 → 9 个（向上取整），8.0 份 → 8 个。
     *
     * <p>取整放在这里而不是界面里，是因为「多凑的那个火药不能白赚射程」这条规则
     * 同时影响消耗和射程，两边各写一份迟早对不上。</p>
     */
    public static int neededItems(int chargeTenths) {
        return (int) Math.ceil(Math.max(0, chargeTenths) / (double) TENTHS_PER_CHARGE);
    }

    /** 实际从槽里扣掉的火药个数（不会超过库存）。 */
    public static int consumedItems(int chargeTenths, int availableItems) {
        return Math.min(neededItems(chargeTenths), Math.max(0, availableItems));
    }

    /**
     * 实际参与射程计算的装药。
     *
     * <p>火药够时就是请求值：8.5 份要 9 个火药，但只算 8.5 份的射程；
     * 火药不够时按到手的整数份封顶（只有 29 个火药就最多打 29.0 份）。</p>
     */
    public static int effectiveChargeTenths(int requestedTenths, int availableItems) {
        int usedItems = consumedItems(requestedTenths, availableItems);
        return Math.min(Math.max(0, requestedTenths), usedItems * TENTHS_PER_CHARGE);
    }

    /**
     * 旧存档迁移：没有 {@code ChargeScale} 记号、且值不超过旧上限时，按「整数份」×10。
     *
     * <p>不能只看 {@code charge <= 64}：新存档里 6.4 份（64）也满足那个条件，
     * 会被误乘成 64.0 份，所以调用方必须把「有没有记号」一起传进来。</p>
     */
    public static int migrateCharge(int storedCharge, boolean hasScaleMarker) {
        int tenths = (!hasScaleMarker && storedCharge <= LEGACY_MAX_CHARGE)
                ? storedCharge * TENTHS_PER_CHARGE
                : storedCharge;
        return clamp(tenths);
    }

    /** 夹到 0..640。 */
    public static int clamp(int chargeTenths) {
        return Math.max(0, Math.min(MAX_CHARGE_TENTHS, chargeTenths));
    }

    /** 内部单位 → 「份」，界面显示和射程计算都走这里，别在各处写 /10。 */
    public static double toCharges(int chargeTenths) {
        return chargeTenths / (double) TENTHS_PER_CHARGE;
    }
}
