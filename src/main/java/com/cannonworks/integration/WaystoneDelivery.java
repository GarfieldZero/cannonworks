package com.cannonworks.integration;

import java.util.Optional;
import javax.annotation.Nullable;
import com.cannonworks.CannonWorks;
import net.blay09.mods.waystones.api.IMutableWaystone;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneManager;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「把一座传送石碑送到落点」——全部 Waystones 类型都收敛在这里。
 *
 * <p>调用前必须确认 {@code ModList.get().isLoaded("waystones")}，
 * 否则类加载会 NoClassDefFoundError。</p>
 */
public final class WaystoneDelivery {
    private WaystoneDelivery() {
    }

    /**
     * 在指定位置放一座石碑，并为发射者激活。
     *
     * @param customName 自定义名字，null/空白则用「空投点 x, z」
     * @return 是否成功放下
     */
    public static boolean deliver(ServerLevel level, BlockPos pos, @Nullable ServerPlayer owner,
                                  @Nullable String customName) {        Optional<IWaystone> placed = WaystonesAPI.placeWaystone(level, pos, WaystoneStyles.DEFAULT);
        if (placed.isEmpty()) {
            return false;
        }
        IWaystone waystone = placed.get();

        // 玩家放置的石碑默认没有名字，列表里全是「未命名石碑」，给它一个可辨认的名字
        if (waystone instanceof IMutableWaystone mutable && !waystone.hasName()) {
            String name = customName != null && !customName.isBlank()
                    ? customName
                    : "空投点 " + pos.getX() + ", " + pos.getZ();
            mutable.setName(name);
            WaystoneManager.get(level.getServer()).updateWaystone(waystone);
            WaystoneSyncManager.sendWaystoneUpdateToAll(level.getServer(), waystone);
        }

        if (owner != null) {
            // placeWaystone 只负责放置与注册，激活必须显式调用
            PlayerWaystoneManager.activateWaystone(owner, waystone);
            boolean activated = PlayerWaystoneManager.isWaystoneActivated(owner, waystone);
            // 关键：activateWaystone 只改服务端玩家数据，客户端列表要靠这个包刷新，
            // 否则玩家要亲自摸一下石碑才会出现（实测踩过）。
            WaystoneSyncManager.sendActivatedWaystones(owner);
            CannonWorks.LOGGER.info("Waystone delivered at {}; activated={}", pos, activated);
        }
        return true;
    }

    /** 立不起来时至少别把石碑弄丢：作为物品掉在落点。 */
    public static void dropAsItem(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(WaystoneStyles.DEFAULT.getBlockRegistryName());
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(block.asItem());
        net.minecraft.world.level.block.Block.popResource(level, BlockPos.containing(at), stack);
    }
}
