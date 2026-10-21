package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.menu.CannonMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CannonWorks.MOD_ID);

    public static final RegistryObject<MenuType<CannonMenu>> CANNON =
            MENUS.register("cannon", () -> IForgeMenuType.create((windowId, inventory, data) ->
                    new CannonMenu(windowId, inventory,
                            (com.cannonworks.block.CannonBlockEntity) inventory.player.level()
                                    .getBlockEntity(data.readBlockPos()))));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
