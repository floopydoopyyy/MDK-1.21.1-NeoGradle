package com.oliver.witchmod.blocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

public final class WitchModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WitchMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BewitchingTableBlockEntity>> BEWITCHING_TABLE =
            BLOCK_ENTITY_TYPES.register("bewitching_table", () -> BlockEntityType.Builder.of(
                    BewitchingTableBlockEntity::new, WitchModBlocks.BEWITCHING_TABLE.get()).build(null));

    private WitchModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
