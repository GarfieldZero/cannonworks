package com.cannonworks.entity;

import com.cannonworks.CannonWorks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * 炮弹的全局推进器。
 *
 * <p>为什么不用 {@code Entity#tick()}：实测在 0 人服务器上，区块可能处于「已加载但不 entity-ticking」
 * 的状态（vanilla 盔甲架都不会下落），靠实体 tick 驱动的投射物会永远冻在第一格。
 * 这里改成由服务端 tick 事件统一推进，与区块 tick 状态解耦；区块加载只用于碰撞查询和渲染。</p>
 *
 * <p>所有方法都只在服务端主线程调用。</p>
 */
public final class ShellManager {
    private static final List<CannonShellEntity> ACTIVE = new ArrayList<>();

    private ShellManager() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShellManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ShellManager::onServerStopped);
    }

    public static void track(CannonShellEntity shell) {
        if (!ACTIVE.contains(shell)) {
            ACTIVE.add(shell);
        }
    }

    public static void untrack(CannonShellEntity shell) {
        ACTIVE.remove(shell);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        // 用快照迭代：advance() 命中后 discard() 会回调 untrack() 修改 ACTIVE，
        // 直接迭代会 ConcurrentModificationException（踩过一次，整服崩）。
        for (CannonShellEntity shell : List.copyOf(ACTIVE)) {
            if (shell.isRemoved()) {
                ACTIVE.remove(shell);
                continue;
            }
            try {
                shell.advance();
            } catch (Exception exception) {
                CannonWorks.LOGGER.error("Cannon shell failed mid-flight at {}", shell.position(), exception);
                ACTIVE.remove(shell);
                shell.discard();
            }
        }
        ACTIVE.removeIf(Entity::isRemoved);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
