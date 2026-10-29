package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.registry.ModBlockEntities;
import com.cannonworks.registry.ModEntities;
import com.cannonworks.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CannonWorks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CANNON_SHELL.get(), CannonShellRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CANNON.get(), CannonRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CannonModel.LAYER, CannonModel::createBodyLayer);
        event.registerLayerDefinition(CannonShellModel.LAYER, CannonShellModel::createBodyLayer);
    }

    /** 1.21.1 用 RegisterMenuScreensEvent，不再在 FMLClientSetupEvent 里手动 register。 */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CANNON.get(), CannonScreen::new);
    }
}
