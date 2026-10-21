package com.cannonworks;

import com.cannonworks.entity.ShellManager;
import com.cannonworks.network.CannonNetwork;
import com.cannonworks.registry.ModBlockEntities;
import com.cannonworks.registry.ModBlocks;
import com.cannonworks.registry.ModEntities;
import com.cannonworks.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
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
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(this::addCreativeTabItems);
        CannonNetwork.register();

        // Forge 事件总线（游戏逻辑事件用）
        MinecraftForge.EVENT_BUS.register(this);
        // 炮弹由服务端 tick 统一推进，不依赖所在区块是否 entity-ticking
        ShellManager.init();

        LOGGER.info("Cannon Works loaded (Waystones present: {})", ModList.get().isLoaded("waystones"));
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.CANNON_ITEM);
        }
    }
}
