package com.cannonworks.payload;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一发炮弹装了什么。
 *
 * <p>按设计文档，落点栏位是<b>互斥</b>的：缓冲物（软投送）和填充物（硬投送）只能二选一，
 * 传送石碑属于「特殊负载」，单独占一类。</p>
 */
public record CannonPayload(Type type, @Nullable BlockState fillerState, int fillerCount) {

    public static final CannonPayload NONE = new CannonPayload(Type.NONE, null, 0);

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
        FILLER
    }

    public static CannonPayload waystone() {
        return new CannonPayload(Type.WAYSTONE, null, 0);
    }

    public static CannonPayload hay() {
        return new CannonPayload(Type.BUFFER_HAY, null, 0);
    }

    public static CannonPayload water() {
        return new CannonPayload(Type.BUFFER_WATER, null, 0);
    }

    public static CannonPayload filler(BlockState state, int count) {
        return new CannonPayload(Type.FILLER, state, Math.max(1, count));
    }

    public String describe() {
        return switch (type) {
            case NONE -> "空";
            case WAYSTONE -> "传送石碑";
            case BUFFER_HAY -> "缓冲物·草垛";
            case BUFFER_WATER -> "缓冲物·水桶";
            case FILLER -> "填充物 " + fillerState.getBlock().getName().getString() + " ×" + fillerCount;
        };
    }
}
