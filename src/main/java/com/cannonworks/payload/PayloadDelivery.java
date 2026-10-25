package com.cannonworks.payload;

import com.cannonworks.entity.ShellManager;
import com.cannonworks.impact.ImpactEffects;
import com.cannonworks.integration.WaystoneDelivery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * 落点效果：把「填充方块 + 货物 + 模式」变成世界里的变化。
 *
 * <p>执行顺序（两种模式共用后半段）：</p>
 * <ol>
 *   <li><b>接触模式</b>先炸出弹坑；<b>空爆模式</b>跳过这一步；</li>
 *   <li>铺填充方块（从坑底往上填，数量守恒）；</li>
 *   <li>最后执行货物：石碑 / TNT / 末影珍珠 / 传送板。</li>
 * </ol>
 *
 * <p>被地形拦截时走的是同一套流程，所以「半路撞山」也能原地生成结构。</p>
 */
public final class PayloadDelivery {

    private static final double CRATER_RADIUS = 3.0;
    private static final double TNT_RADIUS = 6.0;
    private static final int MAX_SUPPORT_DROP = 16;

    private PayloadDelivery() {
    }

    /** 应用负载，返回一句给玩家/日志看的结果描述。 */
    public static String onImpact(ServerLevel level, Vec3 at, Vec3 incoming, CannonPayload payload,
                                  FireMode mode, @Nullable ServerPlayer owner) {
        StringBuilder result = new StringBuilder(mode.label());

        if (mode == FireMode.CONTACT) {
            result.append("，弹坑 ").append(ImpactEffects.carveCrater(level, at, incoming, CRATER_RADIUS)).append(" 格");
        } else {
            result.append("，不炸坑");
        }

        if (payload.hasFiller()) {
            int placed = placeFiller(level, at, incoming, payload, mode);
            result.append("，填充 ").append(placed).append("/").append(payload.fillerCount()).append(" 块");
        }

        if (payload.hasCargo()) {
            result.append("；").append(applyCargo(level, at, incoming, payload, owner));
        }
        return result.toString();
    }

    /** 填充方块：以命中点为中心连通生长，装满装载量为止（1:1，不会多也不会少）。 */
    private static int placeFiller(ServerLevel level, Vec3 hit, Vec3 incoming, CannonPayload payload,
                                   FireMode mode) {
        double radius = CRATER_RADIUS + 1.5;
        if (mode == FireMode.AIRBURST) {
            // 空爆就在命中点原地长——打墙面就贴在墙面，不再投影到墙脚。
            // 如果命中点附近是纯空气（没有可依附的面），才退回地面投影。
            int placed = ImpactEffects.placeBlob(level, hit, payload.fillerState(), payload.fillerCount(), radius);
            if (placed == 0) {
                placed = ImpactEffects.placeBlob(level, groundPoint(level, hit), payload.fillerState(),
                        payload.fillerCount(), radius);
            }
            return placed;
        }
        Vec3 center = hit.subtract(safeNormalize(incoming).scale(0.5));
        return ImpactEffects.placeBlob(level, center, payload.fillerState(), payload.fillerCount(), radius);
    }

    private static String applyCargo(ServerLevel level, Vec3 at, Vec3 incoming, CannonPayload payload,
                                     @Nullable ServerPlayer owner) {
        return switch (payload.cargo()) {
            case NONE -> "";
            case WAYSTONE -> waystone(level, at, owner, payload);
            case TNT -> tnt(level, at);
            case ENDER_PEARL -> enderPearl(level, at, owner);
            case WARP_PLATE -> warpPlate(level, at, payload);
            case WATER -> fluid(level, at, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), "水");
            case LAVA -> fluid(level, at, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), "岩浆");
        };
    }

    /**
     * TNT 货物：在落点放一个真正点燃的 TNT 实体。
     *
     * <p>不手动挖坑——让原版爆炸自己处理，这样：
     * ① 破坏的方块会按原版规则掉落；② 点燃它能引爆周围作为填充方的 TNT，形成链式反应。</p>
     * <p>同时给落点挂 200 tick 的 ticking ticket，否则没人的区块里 TNT 实体根本不会爆。</p>
     */
    private static String tnt(ServerLevel level, Vec3 at) {
        ShellManager.keepAreaTicking(level, at);
        PrimedTnt primed = new PrimedTnt(level, at.x, at.y + 0.5, at.z, null);
        primed.setFuse(20);
        level.addFreshEntity(primed);
        return "TNT 已点燃（20 tick 后起爆）";
    }

    /** 特殊货物：把石碑立在落点，并给发射者激活。 */
    private static String waystone(ServerLevel level, Vec3 at, @Nullable ServerPlayer owner, CannonPayload payload) {
        if (!ModList.get().isLoaded("waystones")) {
            return "未安装传送石碑，货物丢失";
        }
        BlockPos landing = ImpactEffects.findLanding(level, at, 5, 40);
        if (landing == null) {
            WaystoneDelivery.dropAsItem(level, at);
            return "石碑立不起来，已作为物品掉在落点";
        }
        landing = embedIntoFiller(level, landing, payload);
        boolean ok = WaystoneDelivery.deliver(level, landing, owner, payload.cargoName(), payload.cargoBlockState());
        String label = payload.cargoName() != null ? "「" + payload.cargoName() + "」" : "（默认名）";
        if (!ok) {
            return "石碑" + label + "放置失败";
        }
        return owner != null
                ? "石碑" + label + "已放置并为你激活 @ " + fmt(landing)
                : "石碑" + label + "已放置（无发射者）@ " + fmt(landing);
    }

    /** 末影珍珠：把发射者直接传送到落点。 */
    private static String enderPearl(ServerLevel level, Vec3 at, @Nullable ServerPlayer owner) {
        BlockPos landing = ImpactEffects.findLanding(level, at, 3, 16);
        if (landing == null) {
            return "末影珍珠：落点找不到能站的位置";
        }
        if (owner == null) {
            return "末影珍珠落在 " + fmt(landing) + "（无发射者，未传送）";
        }
        owner.teleportTo(level, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                owner.getYRot(), owner.getXRot());
        owner.setDeltaMovement(Vec3.ZERO);
        owner.resetFallDistance();
        owner.invulnerableTime = 40;
        return "末影珍珠把 " + owner.getName().getString() + " 送到 " + fmt(landing);
    }

    /** 液体货物：在落点泼一片（会按原版流体规则流动）。 */
    private static String fluid(ServerLevel level, Vec3 at, BlockState state, String label) {
        BlockPos landing = ImpactEffects.findLanding(level, at, 3, 16);
        BlockPos base = landing != null ? landing : BlockPos.containing(at);
        int placed = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                BlockPos pos = base.offset(dx, 0, dz);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlock(pos, state, 3);
                    placed++;
                }
            }
        }
        return label + "泼洒 " + placed + " 格 @ " + fmt(base);
    }

    /** 传送板：软着陆部署。 */
    private static String warpPlate(ServerLevel level, Vec3 at, CannonPayload payload) {
        if (!ModList.get().isLoaded("waystones")) {
            return "未安装传送石碑，传送板丢失";
        }
        BlockPos landing = ImpactEffects.findLanding(level, at, 3, 16);
        if (landing == null) {
            WaystoneDelivery.dropAsItem(level, at);
            return "传送板：落点找不到平地，已作为物品掉出";
        }
        landing = embedIntoFiller(level, landing, payload);
        return WaystoneDelivery.placeWarpPlate(level, landing)
                ? "传送板已部署 @ " + fmt(landing)
                : "传送板部署失败";
    }

    /** 有填充时把货物往下嵌一层，挖出两格空间，让它长在方块堆里面而不是顶上。 */
    private static BlockPos embedIntoFiller(ServerLevel level, BlockPos landing, CannonPayload payload) {
        if (!payload.hasFiller()) {
            return landing;
        }
        BlockPos embedded = landing.below();
        if (level.getBlockState(embedded).getBlock() != payload.fillerState().getBlock()) {
            return landing;
        }
        level.setBlock(embedded, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(embedded.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        return embedded;
    }

    private static Vec3 groundPoint(ServerLevel level, Vec3 at) {
        BlockPos landing = ImpactEffects.findLanding(level, at, 3, MAX_SUPPORT_DROP);
        return landing == null ? at : Vec3.atBottomCenterOf(landing);
    }

    private static Vec3 safeNormalize(Vec3 vec) {
        return vec.lengthSqr() > 1.0E-6 ? vec.normalize() : new Vec3(0.0, -1.0, 0.0);
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
