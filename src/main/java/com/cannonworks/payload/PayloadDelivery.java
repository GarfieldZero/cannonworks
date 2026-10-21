package com.cannonworks.payload;

import com.cannonworks.impact.ImpactEffects;
import com.cannonworks.integration.WaystoneDelivery;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * 落点效果：把「炮弹装了什么」变成世界里的变化。
 *
 * <p>三条硬规则（防刷）：</p>
 * <ol>
 *   <li>负载在装填/发射时就已扣除，这里只负责「放」，不负责生成；</li>
 *   <li>填充方块的放置量永远 ≤ 装载量；</li>
 *   <li>弹坑炸掉的方块不掉落。</li>
 * </ol>
 */
public final class PayloadDelivery {

    private static final double CRATER_RADIUS = 3.0;
    private static final int MAX_SUPPORT_DROP = 8;

    private PayloadDelivery() {
    }

    /** 应用负载，返回一句给玩家/日志看的结果描述。 */
    public static String onImpact(ServerLevel level, Vec3 at, Vec3 incoming, CannonPayload payload,
                                  @Nullable ServerPlayer owner) {
        return switch (payload.type()) {
            case NONE -> "弹坑 " + ImpactEffects.carveCrater(level, at, incoming, CRATER_RADIUS) + " 格";
            case BUFFER_HAY -> hay(level, at);
            case BUFFER_WATER -> water(level, at, incoming);
            case FILLER -> filler(level, at, incoming, payload);
            case WAYSTONE -> waystone(level, at, incoming, owner, payload);
        };
    }

    /** 缓冲物·草垛：不炸坑，落点正下方一格换成草垛。 */
    private static String hay(ServerLevel level, Vec3 at) {
        BlockPos landing = ImpactEffects.findLanding(level, at, 2, MAX_SUPPORT_DROP);
        if (landing == null) {
            return "草垛投送失败（找不到支撑面）";
        }
        BlockPos below = landing.below();
        level.setBlock(below, Blocks.HAY_BLOCK.defaultBlockState(), 3);
        return "草垛铺在 " + fmt(below);
    }

    /** 缓冲物·水桶：不炸坑，水放在落点旁边（放正下方会被货物方块挤掉）。 */
    private static String water(ServerLevel level, Vec3 at, Vec3 incoming) {
        BlockPos landing = ImpactEffects.findLanding(level, at, 2, MAX_SUPPORT_DROP);
        if (landing == null) {
            return "水桶投送失败（找不到支撑面）";
        }
        Vec3 dir = incoming.lengthSqr() > 1.0E-6 ? incoming.normalize() : new Vec3(0.0, -1.0, 0.0);
        int ox = 0;
        int oz = 0;
        if (Math.abs(dir.x) >= Math.abs(dir.z)) {
            oz = dir.z >= 0 ? 1 : -1;
        } else {
            ox = dir.x >= 0 ? 1 : -1;
        }
        BlockPos waterPos = landing.offset(ox, 0, oz);
        if (!level.getBlockState(waterPos).canBeReplaced()) {
            waterPos = landing;
        }
        level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
        return "水放在 " + fmt(waterPos);
    }

    /** 填充物：正常炸坑，并把填充方块零散嵌进坑壁/坑底（空投建材）。 */
    private static String filler(ServerLevel level, Vec3 at, Vec3 incoming, CannonPayload payload) {
        int carved = ImpactEffects.carveCrater(level, at, incoming, CRATER_RADIUS);
        int placed = scatterFiller(level, at, incoming, payload.fillerState(), payload.fillerCount());
        return "弹坑 " + carved + " 格，填充 " + placed + "/" + payload.fillerCount() + " 块";
    }

    private static int scatterFiller(ServerLevel level, Vec3 hit, Vec3 incoming, BlockState state, int max) {
        Vec3 direction = incoming.lengthSqr() > 1.0E-6 ? incoming.normalize() : new Vec3(0.0, -1.0, 0.0);
        Vec3 center = hit.subtract(direction.scale(0.5));
        BlockPos centerPos = BlockPos.containing(center);
        int r = (int) Math.ceil(CRATER_RADIUS);
        int placed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (placed >= max) {
                        return placed;
                    }
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    if (center.distanceTo(Vec3.atCenterOf(pos)) > CRATER_RADIUS) {
                        continue;
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    BlockPos below = pos.below();
                    if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                        continue;
                    }
                    if (level.random.nextFloat() > 0.6f) {
                        continue;
                    }
                    if (state.canSurvive(level, pos)) {
                        level.setBlock(pos, state, 3);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** 特殊负载：炸坑后把石碑立在坑底，并给发射者激活。 */
    private static String waystone(ServerLevel level, Vec3 at, Vec3 incoming, @Nullable ServerPlayer owner,
                                   CannonPayload payload) {
        int carved = ImpactEffects.carveCrater(level, at, incoming, CRATER_RADIUS);
        if (!ModList.get().isLoaded("waystones")) {
            return "弹坑 " + carved + " 格，但未安装传送石碑，负载丢失";
        }
        // 水面/悬崖落点要往下找得多一些：11×11 范围、最深 40 格（够到海底）
        BlockPos landing = ImpactEffects.findLanding(level, at, 5, 40);
        if (landing == null) {
            WaystoneDelivery.dropAsItem(level, at);
            return "弹坑 " + carved + " 格，石碑立不起来（下方 40 格没有平面），已作为物品掉在落点";
        }
        boolean ok = WaystoneDelivery.deliver(level, landing, owner, payload.customName());
        String label = payload.customName() != null ? "「" + payload.customName() + "」" : "（默认名）";
        String status;
        if (!ok) {
            status = "放置失败";
        } else if (owner != null) {
            status = "已放置并为你激活 @ " + fmt(landing);
        } else {
            status = "已放置（无发射者，未激活）@ " + fmt(landing);
        }
        return "弹坑 " + carved + " 格，传送石碑" + label + status;
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
