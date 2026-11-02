package com.cannonworks.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 可调参数。改完保存后重进世界生效（COMMON 配置）。
 */
public final class CannonConfig {

    public static final ForgeConfigSpec SPEC;

    /** 地图路径点回报间隔（tick）。默认 60 = 3 秒。 */
    public static final ForgeConfigSpec.IntValue TRAIL_INTERVAL_TICKS;

    /** 同一条弹道最多保留几个路径点。 */
    public static final ForgeConfigSpec.IntValue TRAIL_MAX_POINTS;

    /** 穿甲模式最多钻透多硬的方块；黑曜石 = 50，正好是主人点名的上限。 */
    public static final ForgeConfigSpec.IntValue ARMOR_PIERCING_MAX_HARDNESS;

    /** 穿甲隧道半径（方块）。1 = 3×3；0 = 只钻中心 1 格。 */
    public static final ForgeConfigSpec.IntValue ARMOR_PIERCING_TUNNEL_RADIUS;

    /** 每 tick 最多钻掉多少方块，防止一发炮弹把服务器卡死。 */
    public static final ForgeConfigSpec.IntValue ARMOR_PIERCING_BLOCKS_PER_TICK;

    /** 撞到基岩时是否跳弹（打水漂）。关掉就原地结算。 */
    public static final ForgeConfigSpec.BooleanValue ARMOR_PIERCING_BOUNCE;

    /** 最多跳几次；用完就停在落点交付货物。 */
    public static final ForgeConfigSpec.IntValue ARMOR_PIERCING_MAX_BOUNCES;

    /** 法向恢复系数：撞基岩后剩下多少法向速度。 */
    public static final ForgeConfigSpec.DoubleValue ARMOR_PIERCING_RESTITUTION;

    /** 切向摩擦：跳弹时水平方向的速度损失。 */
    public static final ForgeConfigSpec.DoubleValue ARMOR_PIERCING_FRICTION;

    /** 反弹后速度低于这个值就不再弹，直接结算。 */
    public static final ForgeConfigSpec.DoubleValue ARMOR_PIERCING_MIN_BOUNCE_SPEED;

    /**
     * 箱子/刷怪笼这类方块实体：true = 直接炸掉（主人要的默认手感）；
     * false = 当成硬墙停下。炸掉时按原版 destroyBlock 掉落容器内容物。
     */
    public static final ForgeConfigSpec.BooleanValue ARMOR_PIERCING_BREAK_BLOCK_ENTITIES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        TRAIL_INTERVAL_TICKS = builder
                .comment("Ticks between Xaero map trail markers (20 ticks = 1 second). Default 60 = 3s.")
                .defineInRange("trailIntervalTicks", 60, 10, 600);
        TRAIL_MAX_POINTS = builder
                .comment("How many trail markers one shell may keep on the map at once.")
                .defineInRange("trailMaxPoints", 3, 1, 10);

        builder.comment("Armor-piercing shell: tunnel size, hardness cap and bedrock ricochet.")
                .push("armorPiercing");
        ARMOR_PIERCING_MAX_HARDNESS = builder
                .comment("Highest block hardness the shell can drill through. Obsidian is 50.")
                .defineInRange("maxHardness", 50, 0, 100);
        ARMOR_PIERCING_TUNNEL_RADIUS = builder
                .comment("Tunnel radius in blocks. 1 = a 3x3 tunnel; 0 = only the centre line.")
                .defineInRange("tunnelRadius", 1, 0, 3);
        ARMOR_PIERCING_BLOCKS_PER_TICK = builder
                .comment("Hard cap on blocks drilled per tick, so one shell cannot stall the server.")
                .defineInRange("blocksPerTick", 48, 1, 256);
        ARMOR_PIERCING_BOUNCE = builder
                .comment("Ricochet off bedrock instead of stopping. Turn off to stop dead.")
                .define("bounce", true);
        ARMOR_PIERCING_MAX_BOUNCES = builder
                .comment("Maximum ricochets before the shell delivers its payload.")
                .defineInRange("maxBounces", 3, 1, 10);
        ARMOR_PIERCING_RESTITUTION = builder
                .comment("Normal restitution when bouncing (0 = no bounce, 1 = perfectly elastic).")
                .defineInRange("restitution", 0.55, 0.0, 1.0);
        ARMOR_PIERCING_FRICTION = builder
                .comment("Tangential friction per ricochet; keeps stone-skipping from flying forever.")
                .defineInRange("friction", 0.85, 0.0, 1.0);
        ARMOR_PIERCING_MIN_BOUNCE_SPEED = builder
                .comment("Stop ricocheting once the bounced speed drops below this (blocks per tick).")
                .defineInRange("minBounceSpeed", 0.08, 0.0, 5.0);
        ARMOR_PIERCING_BREAK_BLOCK_ENTITIES = builder
                .comment("Chests/spawners: true = destroy them (container contents drop), false = stop.")
                .define("breakBlockEntities", true);
        builder.pop();

        SPEC = builder.build();
    }

    private CannonConfig() {
    }
}
