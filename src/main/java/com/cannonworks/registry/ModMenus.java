package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.menu.CannonMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CannonWorks.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CannonMenu>> CANNON =
            MENUS.register("cannon", () -> IMenuTypeExtension.create((windowId, inventory, data) ->
                    new CannonMenu(windowId, inventory,
                            (com.cannonworks.block.CannonBlockEntity) inventory.player.level()
                                    .getBlockEntity(data.readBlockPos()))));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
