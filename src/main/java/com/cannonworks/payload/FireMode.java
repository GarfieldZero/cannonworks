package com.cannonworks.payload;

/** 开火模式：决定落点要不要先炸出弹坑。 */
public enum FireMode {
    /** 接触：炸坑 → 填充方块 → 货物生效。 */
    CONTACT("接触"),
    /** 空爆：不炸坑 → 填充方块 → 货物生效。 */
    AIRBURST("空爆");

    private final String label;

    FireMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public FireMode next() {
        return this == CONTACT ? AIRBURST : CONTACT;
    }

    public static FireMode byName(String name) {
        return "airburst".equalsIgnoreCase(name) ? AIRBURST : CONTACT;
    }
}
