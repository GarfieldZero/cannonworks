package com.cannonworks;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Cannon Works —— 大炮主题模组。
 *
 * <p>开发环境：Minecraft 1.20.1 / Forge 47.4.1 / Java 17。
 * 与「传送石碑（Waystones）」为<b>软依赖</b>：未安装时本模组照常加载，
 * 只是所有联动入口会自动降级为「未检测到 Waystones」。</p>
 */
@Mod(CannonWorks.MOD_ID)
public final class CannonWorks {
    public static final String MOD_ID = "cannonworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CannonWorks() {
        // 模组事件总线（注册物品/方块/实体等用）
        FMLJavaModLoadingContext.get().getModEventBus();
        // Forge 事件总线（游戏逻辑事件用）
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Cannon Works loaded (Waystones present: {})", ModList.get().isLoaded("waystones"));
    }
}
