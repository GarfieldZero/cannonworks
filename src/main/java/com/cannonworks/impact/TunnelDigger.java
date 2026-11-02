package com.cannonworks.impact;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 穿甲弹的“钻头”：在命中点周围挖掉一个立方体隧道截面。
 *
 * <p>规则全在这里，ShellManager 只管决定“这一发命中了、该挖了”：</p>
 * <ul>
 *   <li>空气、可替换方块、纯流体跳过；</li>
 *   <li>硬度 &lt; 0（基岩/屏障/命令方块）或超过上限的方块跳过，由飞行层去跳弹/结算；</li>
 *   <li>普通方块直接 {@code setBlock(AIR)}，<b>不掉落</b>，否则穿甲弹就成了自动矿机；</li>
 *   <li>箱子/刷怪笼这类方块实体：按配置直接炸掉，容器内容物按原版掉落
 *       （主人点名要的默认手感；不想要就关配置，飞行层会把它当硬墙）。</li>
 * </ul>
 *
 * <p>每 tick 有方块上限，别让一发炮弹把服务器挖成 PPT。</p>
 */
public final class TunnelDigger {

    private TunnelDigger() {
    }

    /**
     * 以命中方块为中心挖一个 (2r+1)³ 的立方体。
     *
     * @param level              服务端世界
     * @param center             命中的方块（不是精确命中点，避免球心卡在面边界上）
     * @param radius             半径；1 = 3×3 截面，0 = 只挖中心
     * @param maxHardness        能挖动的最大硬度
     * @param maxBlocks          本次调用最多挖多少块（每 tick 预算）
     * @param breakBlockEntities 是否炸掉方块实体
     * @return 实际挖掉的方块数
     */
    public static int digTunnel(ServerLevel level, BlockPos center, int radius, double maxHardness,
                                int maxBlocks, boolean breakBlockEntities) {
        int broken = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (broken >= maxBlocks) {
                        return broken;
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    // 纯流体不挖（Clipping 本来也不看流体），水浸方块仍然算实心
                    if (state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LiquidBlock) {
                        continue;
                    }
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0.0f || hardness > maxHardness) {
                        continue;
                    }
                    if (state.hasBlockEntity()) {
                        if (!breakBlockEntities) {
                            continue;
                        }
                        // 箱子这类容器：先把内容物倒出来，再炸方块。
                        // 不能只靠 destroyBlock(pos, true)：没有玩家上下文时原版不会掉容器内容。
                        if (level.getBlockEntity(pos) instanceof Container container) {
                            Containers.dropContents(level, pos, container);
                        }
                        // 箱子/刷怪笼：直接炸掉，容器内容物按原版规则掉落
                        level.destroyBlock(pos, true);
                        level.sendParticles(ParticleTypes.EXPLOSION,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                1, 0.0, 0.0, 0.0, 0.0);
                        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.2f);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    broken++;
                }
            }
        }
        return broken;
    }
}
