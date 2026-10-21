package com.cannonworks.impact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 落点几何。
 *
 * <p>关键原则：<b>弹坑是「以命中点为中心的三维球」</b>，不是「投影到地面高度图上的圆」。
 * 打在山壁上就在山壁上炸出一个洞（洞底就是洞里的地面），绝不会把效果平移到山脚。</p>
 */
public final class ImpactEffects {

    /** 单发最多削掉多少方块，防止一发把服务器卡死。 */
    private static final int MAX_BLOCKS = 512;

    private ImpactEffects() {
    }

    /**
     * 在命中点挖一个球形弹坑。
     *
     * @param level    服务端世界
     * @param hit      射线命中的精确位置（在方块表面上）
     * @param incoming 炮弹来向（用于把球心往材质里推半格）
     * @param radius   半径
     * @return 实际削掉的方块数
     */
    public static int carveCrater(ServerLevel level, Vec3 hit, Vec3 incoming, double radius) {
        Vec3 direction = incoming.lengthSqr() > 1.0E-6 ? incoming.normalize() : new Vec3(0.0, -1.0, 0.0);
        // 命中点在方块表面；球心往里推 0.5 格，弹坑才有一半嵌在材质里
        Vec3 center = hit.subtract(direction.scale(0.5));
        BlockPos centerPos = BlockPos.containing(center);
        int r = (int) Math.ceil(radius);
        int removed = 0;

        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (removed >= MAX_BLOCKS) {
                        break outer;
                    }
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    if (center.distanceTo(Vec3.atCenterOf(pos)) > radius) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    // 容器/方块实体先不动：防刷，也防止把别人的箱子炸了（M2 再做配置）
                    if (state.hasBlockEntity()) {
                        continue;
                    }
                    // 硬度 < 0 = 基岩、屏障、命令方块这类不可破坏方块
                    if (state.getDestroySpeed(level, pos) < 0.0f) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * 给落点找一个「能站人」的支撑位：在命中点附近 7×7 的柱子里往下探，
     * 取<b>上方有两格空气</b>且离命中点最近的那个面。
     *
     * <p>为什么不是简单的「往下找第一块实心」：炸出来的坑会有悬垂边，
     * 那样选出来的位置头顶是实心的，石碑会被埋进墙里。</p>
     *
     * @return 支撑面上方的空气格；找不到返回 null
     */
    public static BlockPos findLanding(ServerLevel level, Vec3 hit, int radius, int maxDrop) {
        BlockPos base = BlockPos.containing(hit);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int i = 0; i <= maxDrop; i++) {
                    BlockPos pos = base.offset(dx, -i, dz);
                    if (level.isOutsideBuildHeight(pos)) {
                        break;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.canBeReplaced()) {
                        continue;
                    }
                    BlockPos landing = pos.above();
                    if (level.isEmptyBlock(landing) && level.isEmptyBlock(landing.above())) {
                        double score = base.distSqr(landing);
                        if (score < bestScore) {
                            bestScore = score;
                            best = landing;
                        }
                    }
                    break; // 这一列已经碰到地面了
                }
            }
        }
        return best;
    }
}
