package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.entity.CannonShellEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CannonWorks.MOD_ID);

    public static final RegistryObject<EntityType<CannonShellEntity>> CANNON_SHELL =
            ENTITY_TYPES.register("cannon_shell", () -> EntityType.Builder
                    .<CannonShellEntity>of(CannonShellEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(16)
                    // 每 tick 同步一次，远距离飞行才不会「一卡一卡」
                    .updateInterval(1)
                    .build("cannon_shell"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
