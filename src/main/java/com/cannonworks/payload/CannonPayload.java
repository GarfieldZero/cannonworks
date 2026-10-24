package com.cannonworks.payload;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一发炮弹装了什么。
 *
 * <p>按设计文档，落点栏位是<b>互斥</b>的：缓冲物（软投送）和填充物（硬投送）只能二选一，
 * 传送石碑属于「特殊负载」，单独占一类。</p>
 *
 * @param customName 特殊负载的自定义名字（目前只有传送石碑用），null 表示用默认名
 */
public record CannonPayload(Type type, @Nullable BlockState fillerState, int fillerCount,
                            @Nullable String customName) {

    public static final CannonPayload NONE = new CannonPayload(Type.NONE, null, 0, null);

    private static final int MAX_NAME_LENGTH = 32;

    public enum Type {
        /** 空：纯弹坑。 */
        NONE,
        /** 特殊负载：落地放一座传送石碑并给发射者激活。 */
        WAYSTONE,
        /** 缓冲物·草垛：无弹坑，落点下方换成草垛。 */
        BUFFER_HAY,
        /** 缓冲物·水桶：无弹坑，落点旁边放水。 */
        BUFFER_WATER,
        /** 填充物：有弹坑，填充方块零散嵌在坑里，相当于空投建材。 */
        FILLER,
        /** 爆破：大半径弹坑 + 强化爆炸表现。 */
        TNT,
        /** 末影珍珠：把发射者直接传送到落点。 */
        ENDER_PEARL
    }

    public static CannonPayload waystone() {
        return new CannonPayload(Type.WAYSTONE, null, 0, null);
    }

    public static CannonPayload waystone(@Nullable String name) {
        return new CannonPayload(Type.WAYSTONE, null, 0, sanitize(name));
    }

    public static CannonPayload hay() {
        return new CannonPayload(Type.BUFFER_HAY, null, 0, null);
    }

    public static CannonPayload water() {
        return new CannonPayload(Type.BUFFER_WATER, null, 0, null);
    }

    public static CannonPayload filler(BlockState state, int count) {
        return new CannonPayload(Type.FILLER, state, Math.max(1, count), null);
    }

    public static CannonPayload tnt() {
        return new CannonPayload(Type.TNT, null, 0, null);
    }

    public static CannonPayload enderPearl() {
        return new CannonPayload(Type.ENDER_PEARL, null, 0, null);
    }

    public String describe() {
        return switch (type) {
            case NONE -> "空";
            case WAYSTONE -> customName != null ? "传送石碑「" + customName + "」" : "传送石碑";
            case BUFFER_HAY -> "缓冲物·草垛";
            case BUFFER_WATER -> "缓冲物·水桶";
            case FILLER -> "填充物 " + fillerState.getBlock().getName().getString() + " ×" + fillerCount;
            case TNT -> "TNT 爆破";
            case ENDER_PEARL -> "末影珍珠";
        };
    }

    /** 去掉控制字符和包裹的引号、裁掉首尾空白，空串当没填，最长 32 字。 */
    @Nullable
    private static String sanitize(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.trim();
        if (cleaned.length() >= 2
                && ((cleaned.startsWith("'") && cleaned.endsWith("'"))
                || (cleaned.startsWith("\"") && cleaned.endsWith("\"")))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        cleaned = cleaned.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }
}
