package com.cannonworks.registry;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlock;
import com.cannonworks.block.CannonPartBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, CannonWorks.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, CannonWorks.MOD_ID);

    public static final DeferredHolder<Block, CannonBlock> CANNON = BLOCKS.register("cannon",
            () -> new CannonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> CANNON_ITEM = ITEMS.register("cannon",
            () -> new BlockItem(CANNON.get(), new Item.Properties()));

    /** 不可见底座占位块：只负责碰撞与右键转发，没有物品形态。 */
    public static final DeferredHolder<Block, CannonPartBlock> CANNON_PART = BLOCKS.register("cannon_part",
            () -> new CannonPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
