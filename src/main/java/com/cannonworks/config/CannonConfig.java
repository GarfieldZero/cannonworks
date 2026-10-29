package com.cannonworks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 可调参数。改完保存后重进世界生效（COMMON 配置）。
 */
public final class CannonConfig {

    public static final ModConfigSpec SPEC;

    /** 地图路径点回报间隔（tick）。默认 60 = 3 秒。 */
    public static final ModConfigSpec.IntValue TRAIL_INTERVAL_TICKS;

    /** 同一条弹道最多保留几个路径点。 */
    public static final ModConfigSpec.IntValue TRAIL_MAX_POINTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        TRAIL_INTERVAL_TICKS = builder
                .comment("Ticks between Xaero map trail markers (20 ticks = 1 second). Default 60 = 3s.")
                .defineInRange("trailIntervalTicks", 60, 10, 600);
        TRAIL_MAX_POINTS = builder
                .comment("How many trail markers one shell may keep on the map at once.")
                .defineInRange("trailMaxPoints", 3, 1, 10);
        SPEC = builder.build();
    }

    private CannonConfig() {
    }
}
