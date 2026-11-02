package com.cannonworks.payload;

/**
 * 开火模式：决定落点要不要先炸出弹坑，以及飞行中要不要钻地形。
 *
 * <p>界面显示走 {@link #translationKey()}（客户端按玩家语言渲染）；
 * {@link #label()} 保留给服务端日志和命令反馈——那里没有「玩家语言」这回事，
 * 硬翻译反而会让中文服务端日志变成英文。</p>
 */
public enum FireMode {
    /** 接触：炸坑 → 填充方块 → 货物生效。 */
    CONTACT("接触", "gui.cannonworks.cannon.fuse.contact"),
    /** 空爆：不炸坑 → 填充方块 → 货物生效。 */
    AIRBURST("空爆", "gui.cannonworks.cannon.fuse.airburst"),
    /**
     * 穿甲：飞行中钻透黑曜石及以下硬度的方块；撞基岩会跳弹；
     * 停下来的地方不再炸坑，直接铺填充/放货物。
     */
    PIERCING("穿甲", "gui.cannonworks.cannon.fuse.piercing");

    private final String label;
    private final String translationKey;

    FireMode(String label, String translationKey) {
        this.label = label;
        this.translationKey = translationKey;
    }

    /** 给服务端日志/命令用的固定中文。 */
    public String label() {
        return label;
    }

    /** 给 GUI 用的语言键。 */
    public String translationKey() {
        return translationKey;
    }

    public FireMode next() {
        return switch (this) {
            case CONTACT -> AIRBURST;
            case AIRBURST -> PIERCING;
            case PIERCING -> CONTACT;
        };
    }

    public static FireMode byName(String name) {
        if ("airburst".equalsIgnoreCase(name)) {
            return AIRBURST;
        }
        if ("piercing".equalsIgnoreCase(name)) {
            return PIERCING;
        }
        return CONTACT;
    }
}
