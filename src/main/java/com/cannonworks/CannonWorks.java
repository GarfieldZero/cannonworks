package com.cannonworks;

import com.cannonworks.config.CannonConfig;
import com.cannonworks.entity.ShellManager;
import com.cannonworks.network.CannonNetwork;
import com.cannonworks.registry.ModBlockEntities;
import com.cannonworks.registry.ModBlocks;
import com.cannonworks.registry.ModEntities;
import com.cannonworks.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

/**
 * Cannon Works —— 大炮主题模组（Minecraft 1.21.1 / NeoForge 21.1）。
 *
 * <p>与「传送石碑（Waystones）」仍为<b>软依赖</b>：未安装时模组照常加载，
 * 联动入口（WaystoneTargets / WaystoneDelivery）在调用前都会先确认 ModList 里有没有。</p>
 *
 * <p>这是 1.21.1 + NeoForge 分支；1.20.1 + Forge 的主线保留在 main 分支。</p>
 */
@Mod(CannonWorks.MOD_ID)
public final class CannonWorks {
    public static final String MOD_ID = "cannonworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CannonWorks(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CannonConfig.SPEC);

        ModEntities.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(this::addCreativeTabItems);
        // 1.21.1 的网络包改成 payload 注册制，必须挂在 mod 事件总线上
        modBus.addListener(CannonNetwork::register);

        // 炮弹由服务端 tick 统一推进，不依赖所在区块是否 entity-ticking
        ShellManager.init();

        LOGGER.info("Cannon Works loaded for NeoForge 1.21.1 (Waystones present: {})",
                ModList.get().isLoaded("waystones"));
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.CANNON_ITEM.get());
        }
    }
}
