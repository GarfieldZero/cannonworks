package com.cannonworks.block;

import com.cannonworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 大炮底座的碰撞占位结构。
 *
 * <p>控制器只有一个方块；Minecraft 只会查询控制器自身 1×1 所属格里的
 * VoxelShape，所以不能用“超大 shape”覆盖底座。这里用一圈不可见占位方块
 * 罩住炮台可能转到的范围（5×5 格、2 格高），每个占位块的碰撞形状再由
 * {@link CannonCollision} 按当前方位角裁出来。</p>
 *
 * <p>关键点：占位块只负责“查询格子”，不再是一整块实心方块。空网格的
 * collision shape 是 empty，玩家可以正常走过去；只有模型真正占到的部分
 * 才有碰撞，所以不会再有空气墙。</p>
 */
public final class CannonMultiblock {

    /** 炮台转到任意角度时可能占到的外围范围：5×5。 */
    public static final int X_MIN = -2;
    public static final int X_MAX = 2;
    public static final int Z_MIN = -2;
    public static final int Z_MAX = 2;
    public static final int HEIGHT = 2;
    /** 清旧版占位块时多查一层：旧 5×4×3 版本在 y+2 还有一层。 */
    private static final int CLEAR_HEIGHT = 3;

    private static boolean removing;

    private CannonMultiblock() {
    }

    public static boolean isRemoving() {
        return removing;
    }

    public static void place(ServerLevel level, BlockPos anchor) {
        if (removing) {
            return;
        }
        boolean previous = removing;
        removing = true;
        try {
            // 清掉不属于当前 5×5×2 范围的旧占位块（包括上一版 4×4×2 / 5×4×3）。
            for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                for (int dx = X_MIN; dx <= X_MAX; dx++) {
                    for (int dz = Z_MIN; dz <= Z_MAX; dz++) {
                        BlockPos pos = anchor.offset(dx, dy, dz);
                        if (!pos.equals(anchor)
                                && !isPart(anchor, pos)
                                && level.getBlockState(pos).is(ModBlocks.CANNON_PART.get())) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dx = X_MIN; dx <= X_MAX; dx++) {
                    for (int dz = Z_MIN; dz <= Z_MAX; dz++) {
                        BlockPos pos = anchor.offset(dx, dy, dz);
                        if (pos.equals(anchor)) {
                            continue;
                        }
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, ModBlocks.CANNON_PART.get().defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        } finally {
            removing = previous;
        }
    }

    public static BlockPos findMaster(Level level, BlockPos part) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos candidate = part.offset(dx, dy, dz);
                    if (level.getBlockEntity(candidate) instanceof CannonBlockEntity && isPart(candidate, part)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isPart(BlockPos anchor, BlockPos part) {
        int dx = part.getX() - anchor.getX();
        int dy = part.getY() - anchor.getY();
        int dz = part.getZ() - anchor.getZ();
        return dx >= X_MIN && dx <= X_MAX
                && dz >= Z_MIN && dz <= Z_MAX
                && dy >= 0 && dy < HEIGHT;
    }

    /** 控制器被正常破坏时调用：只清占位块，控制器自身的掉落由方块 loot 处理。 */
    public static void removeParts(Level level, BlockPos anchor) {
        if (level.isClientSide) {
            return;
        }
        boolean previous = removing;
        removing = true;
        try {
            clearParts(level, anchor);
        } finally {
            removing = previous;
        }
    }

    /** 玩家破坏任一占位块：把控制器和其余占位块一起移除，并让控制器正常掉落。 */
    public static void breakFromPart(ServerLevel level, BlockPos anchor) {
        if (removing) {
            return;
        }
        boolean previous = removing;
        removing = true;
        try {
            clearParts(level, anchor);
            // destroyBlock 会走 CannonBlock.onRemove，掉落物品并清理内容物。
            level.destroyBlock(anchor, true);
        } finally {
            removing = previous;
        }
    }

    private static void clearParts(Level level, BlockPos anchor) {
        for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
            for (int dx = X_MIN; dx <= X_MAX; dx++) {
                for (int dz = Z_MIN; dz <= Z_MAX; dz++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    if (!pos.equals(anchor) && level.getBlockState(pos).is(ModBlocks.CANNON_PART.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }
}
