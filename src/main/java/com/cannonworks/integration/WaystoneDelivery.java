package com.cannonworks.integration;

import java.util.Optional;
import javax.annotation.Nullable;
import com.cannonworks.CannonWorks;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneStyle;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.block.SharestoneBlock;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「把一座传送石碑送到落点」——全部 Waystones 类型都收敛在这里。
 *
 * <p>调用前必须确认 {@code ModList.get().isLoaded("waystones")}，
 * 否则类加载会 NoClassDefFoundError。</p>
 *
 * <p>1.21.1 适配：`IWaystone`→`Waystone`、`IMutableWaystone`→`MutableWaystone`、
 * 名字是 `Component`；`WaystoneStyles.DEFAULT` 没了，改为按 `waystones:waystone` 方块取样式；
 * `updateWaystone`/`sendActivatedWaystones` 也没了，用新 API 的激活与同步方法。</p>
 */
public final class WaystoneDelivery {

    /** 默认石碑方块。Waystones 21.1 删掉了 WaystoneStyles.DEFAULT，只能按注册名反查。 */
    private static final ResourceLocation DEFAULT_WAYSTONE_BLOCK =
            ResourceLocation.fromNamespaceAndPath("waystones", "waystone");

    private WaystoneDelivery() {
    }

    /**
     * 在指定位置放一座石碑，并为发射者激活。
     *
     * @param customName 自定义名字，null/空白则用「空投点 x, z」
     * @param styleBlock 具体石碑方块（苔石/沙岩/黑石…），null 用默认样式
     * @return 是否成功放下
     */
    public static boolean deliver(ServerLevel level, BlockPos pos, @Nullable ServerPlayer owner,
                                  @Nullable String customName, @Nullable BlockState styleBlock) {
        Optional<Waystone> placed;
        if (styleBlock != null && styleBlock.getBlock() instanceof SharestoneBlock sharestone) {
            // 分享石走另一套 API：颜色是方块自带属性，不是 WaystoneStyle
            placed = WaystonesAPI.placeSharestone(level, pos, sharestone.getColor());
        } else {
            WaystoneStyle style = styleBlock != null
                    ? WaystoneStyles.getStyle(styleBlock.getBlock())
                    : WaystoneStyles.getStyle(BuiltInRegistries.BLOCK.get(DEFAULT_WAYSTONE_BLOCK));
            placed = WaystonesAPI.placeWaystone(level, pos, style);
        }
        if (placed.isEmpty()) {
            return false;
        }
        Waystone waystone = placed.get();

        // 玩家放置的石碑默认没有名字，列表里全是「未命名石碑」，给它一个可辨认的名字
        if (waystone instanceof MutableWaystone mutable && !waystone.hasName()) {
            String name = customName != null && !customName.isBlank()
                    ? customName
                    : "空投点 " + pos.getX() + ", " + pos.getZ();
            mutable.setName(Component.literal(name));
            WaystoneSyncManager.sendWaystoneUpdateToAll(level.getServer(), waystone);
        }

        if (owner != null) {
            WaystonesAPI.activateWaystone(owner, waystone);
            boolean activated = WaystonesAPI.isWaystoneActivated(owner, waystone);
            CannonWorks.LOGGER.info("Waystone delivered at {}; activated={}", pos, activated);
        }
        return true;
    }

    /** 部署一块传送板（warp plate）。 */
    public static boolean placeWarpPlate(ServerLevel level, BlockPos pos) {
        return WaystonesAPI.placeWarpPlate(level, pos).isPresent();
    }

    /** 立不起来时至少别把石碑弄丢：作为物品掉在落点。 */
    public static void dropAsItem(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        var block = BuiltInRegistries.BLOCK.get(DEFAULT_WAYSTONE_BLOCK);
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(block.asItem());
        net.minecraft.world.level.block.Block.popResource(level, BlockPos.containing(at), stack);
    }
}
