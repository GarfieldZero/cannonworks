package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.entity.CannonShellEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CannonWorks.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CannonShellEntity>> CANNON_SHELL =
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
