package com.cannonworks.integration;

import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 所有对 Waystones 类型的直接引用都收敛在这个类里。
 *
 * <p>Waystones 未安装时本类<b>不能</b>被加载（否则 NoClassDefFoundError），
 * 因此调用方必须先 {@code ModList.get().isLoaded("waystones")}。
 * 对外暴露的返回值只用 JDK 类型（String / Optional&lt;Component&gt;），
 * 避免 Waystones 类型泄漏到命令、实体等调用方的常量池里。</p>
 *
 * <p>1.21.1 适配：Waystones 21.1 把 `IWaystone` 改成了 `Waystone`、名字改成 `Component`，
 * 传送改走 TeleportContext（旧的 forceTeleportToWaystone 已删除）。</p>
 */
public final class WaystoneTargets {
    private WaystoneTargets() {
    }

    /** 玩家已激活的石碑，格式化为 "名字 @ 维度 (x, y, z)"。 */
    public static List<String> describeActivated(ServerPlayer player) {
        return WaystonesAPI.getActivatedWaystones(player).stream()
                .map(WaystoneTargets::describe)
                .toList();
    }

    /** 距离玩家最近的石碑（不要求已激活），没有则返回 empty。 */
    public static Optional<String> describeNearest(ServerPlayer player) {
        return WaystonesAPI.getNearestWaystone(player).map(WaystoneTargets::describe);
    }

    /**
     * 强制把玩家传送到同名（忽略大小写）的已激活石碑，绕过冷却/经验消耗。
     *
     * @return empty 表示成功；否则是需要展示给玩家的失败原因
     */
    public static Optional<Component> teleport(ServerPlayer player, String name) {
        Optional<Waystone> target = WaystonesAPI.getActivatedWaystones(player).stream()
                .filter(waystone -> waystone.hasName()
                        && waystone.getName().getString().equalsIgnoreCase(name))
                .findFirst();
        if (target.isEmpty()) {
            return Optional.of(Component.literal("没有找到名为 \"" + name + "\" 的已激活传送石碑"));
        }

        Either<WaystoneTeleportContext, WaystoneTeleportError> created =
                WaystonesAPI.createDefaultTeleportContext(player, target.get(), context -> {
                });
        if (created.left().isEmpty()) {
            WaystoneTeleportError error = created.right().orElse(null);
            return Optional.of(error != null ? error.getComponent() : Component.literal("传送失败"));
        }

        Either<List<Entity>, WaystoneTeleportError> result = WaystonesAPI.forceTeleport(created.left().get());
        if (result.left().isPresent()) {
            return Optional.empty();
        }

        WaystoneTeleportError error = result.right().orElse(null);
        return Optional.of(error != null ? error.getComponent() : Component.literal("传送失败"));
    }

    private static String describe(Waystone waystone) {
        BlockPos pos = waystone.getPos();
        return "%s @ %s (%d, %d, %d)".formatted(
                waystone.getName().getString(),
                waystone.getDimension().location(),
                pos.getX(), pos.getY(), pos.getZ());
    }
}
