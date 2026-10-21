package com.cannonworks.integration;

import java.util.Optional;
import javax.annotation.Nullable;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
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
     * @return 是否成功放下
     */
    public static boolean deliver(ServerLevel level, BlockPos pos, @Nullable ServerPlayer owner) {
        Optional<IWaystone> placed = WaystonesAPI.placeWaystone(level, pos, WaystoneStyles.DEFAULT);
        if (placed.isEmpty()) {
            return false;
        }
        if (owner != null) {
            // placeWaystone 只负责放置与注册，激活必须显式调用
            PlayerWaystoneManager.activateWaystone(owner, placed.get());
        }
        return true;
    }
}
