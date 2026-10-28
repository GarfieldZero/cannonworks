package com.cannonworks.payload;

/**
 * 开火模式：决定落点要不要先炸出弹坑。
 *
 * <p>界面显示走 {@link #translationKey()}（客户端按玩家语言渲染）；
 * {@link #label()} 保留给服务端日志和命令反馈——那里没有「玩家语言」这回事，
 * 硬翻译反而会让中文服务端日志变成英文。</p>
 */
public enum FireMode {
    /** 接触：炸坑 → 填充方块 → 货物生效。 */
    CONTACT("接触", "gui.cannonworks.cannon.fuse.contact"),
    /** 空爆：不炸坑 → 填充方块 → 货物生效。 */
    AIRBURST("空爆", "gui.cannonworks.cannon.fuse.airburst");

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
        return this == CONTACT ? AIRBURST : CONTACT;
    }

    public static FireMode byName(String name) {
        return "airburst".equalsIgnoreCase(name) ? AIRBURST : CONTACT;
    }
}
