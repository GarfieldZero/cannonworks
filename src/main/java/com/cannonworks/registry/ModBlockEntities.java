package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CannonWorks.MOD_ID);

    public static final RegistryObject<BlockEntityType<CannonBlockEntity>> CANNON =
            BLOCK_ENTITIES.register("cannon", () -> BlockEntityType.Builder
                    .of(CannonBlockEntity::new, ModBlocks.CANNON.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
