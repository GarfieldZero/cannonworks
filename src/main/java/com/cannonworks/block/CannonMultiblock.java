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
 * <p>大炮视觉上约 4×3 格。控制器只有一个方块；Minecraft 只会查询
 * 控制器自身 1×1 所属格里的 VoxelShape，所以不能用“超大 shape”覆盖
 * 底座。这里用不可见占位方块填满底座（4×4 足迹、2 格高），由占位方块
 * 负责碰撞和右键转发。</p>
 *
 * <p>4×4 是旋转对称的，因此东/南/西/北四种朝向都不用重新排布。
 * 这些方块没有 BlockEntity；右键/破坏时在附近扫描控制器。</p>
 */
public final class CannonMultiblock {

    /** 当前占位足迹：4×4，高 2。 */
    public static final int X_MIN = -2;
    public static final int X_MAX = 1;
    public static final int Z_MIN = -2;
    public static final int Z_MAX = 1;
    public static final int HEIGHT = 2;

    /** 清理旧版本 5×4×3 足迹时使用的外围范围。 */
    private static final int CLEAR_X_MIN = -2;
    private static final int CLEAR_X_MAX = 2;
    private static final int CLEAR_Z_MIN = -2;
    private static final int CLEAR_Z_MAX = 2;
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
            // 清掉旧版 5×4×3 足迹里不属于新 4×4×2 的占位块。
            for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                for (int dx = CLEAR_X_MIN; dx <= CLEAR_X_MAX; dx++) {
                    for (int dz = CLEAR_Z_MIN; dz <= CLEAR_Z_MAX; dz++) {
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
                for (int dy = -2; dy <= 0; dy++) {
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
            for (int dx = CLEAR_X_MIN; dx <= CLEAR_X_MAX; dx++) {
                for (int dz = CLEAR_Z_MIN; dz <= CLEAR_Z_MAX; dz++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    if (!pos.equals(anchor) && level.getBlockState(pos).is(ModBlocks.CANNON_PART.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }
}
