package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CannonWorks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CannonBlockEntity>> CANNON =
            BLOCK_ENTITIES.register("cannon", () -> BlockEntityType.Builder
                    .of(CannonBlockEntity::new, ModBlocks.CANNON.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
