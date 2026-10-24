package com.cannonworks.payload;

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

    /** 填充方块：从坑底往上铺，铺满装载量为止（不会凭空多出来）。 */
    private static int placeFiller(ServerLevel level, Vec3 hit, Vec3 incoming, CannonPayload payload,
                                   FireMode mode) {
        Vec3 center = mode == FireMode.AIRBURST
                ? groundPoint(level, hit)
                : hit.subtract(safeNormalize(incoming).scale(0.5));
        BlockPos centerPos = BlockPos.containing(center);
        BlockState state = payload.fillerState();
        int radius = (int) Math.ceil(CRATER_RADIUS + 1);

        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    if (center.distanceTo(Vec3.atCenterOf(pos)) > CRATER_RADIUS + 0.8) {
                        continue;
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    BlockPos below = pos.below();
                    if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                        continue;
                    }
                    if (!state.canSurvive(level, pos)) {
                        continue;
                    }
                    candidates.add(pos);
                }
            }
        }
        candidates.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingDouble(pos -> pos.distSqr(centerPos)));

        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= payload.fillerCount()) {
                break;
            }
            level.setBlock(pos, state, 3);
            placed++;
        }
        return placed;
    }

    private static String applyCargo(ServerLevel level, Vec3 at, Vec3 incoming, CannonPayload payload,
                                     @Nullable ServerPlayer owner) {
        return switch (payload.cargo()) {
            case NONE -> "";
            case WAYSTONE -> waystone(level, at, owner, payload);
            case TNT -> tnt(level, at, incoming);
            case ENDER_PEARL -> enderPearl(level, at, owner);
            case WARP_PLATE -> warpPlate(level, at);
        };
    }

    /** 爆破：半径 6 的额外爆炸（接触模式下会和基础弹坑叠加）。 */
    private static String tnt(ServerLevel level, Vec3 at, Vec3 incoming) {
        int carved = ImpactEffects.carveCrater(level, at, incoming, TNT_RADIUS);
        for (int i = 0; i < 8; i++) {
            double dx = (level.random.nextDouble() - 0.5) * 6.0;
            double dy = (level.random.nextDouble() - 0.5) * 4.0;
            double dz = (level.random.nextDouble() - 0.5) * 6.0;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    at.x + dx, at.y + dy, at.z + dz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.BLOCKS, 8.0f, 0.4f);
        return "TNT 爆破 " + carved + " 格";
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

    /** 传送板：软着陆部署。 */
    private static String warpPlate(ServerLevel level, Vec3 at) {
        if (!ModList.get().isLoaded("waystones")) {
            return "未安装传送石碑，传送板丢失";
        }
        BlockPos landing = ImpactEffects.findLanding(level, at, 3, 16);
        if (landing == null) {
            WaystoneDelivery.dropAsItem(level, at);
            return "传送板：落点找不到平地，已作为物品掉出";
        }
        return WaystoneDelivery.placeWarpPlate(level, landing)
                ? "传送板已部署 @ " + fmt(landing)
                : "传送板部署失败";
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
