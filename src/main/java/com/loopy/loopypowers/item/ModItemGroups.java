package com.loopy.loopypowers.item;

import com.loopy.loopypowers.Loopypowers;
import com.loopy.loopypowers.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItemGroups {
    // 1. Create the register for Creative Tabs
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Loopypowers.MOD_ID);

    // 2. Register the specific tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOOPYPOWERS_GROUP =
            CREATIVE_MODE_TABS.register("loopypowers_group", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.CELESTIAL_SHARD.get()))
                    .title(Component.literal("Loopy Powers"))
                    .displayItems((parameters, output) -> {
                        // Items
                        output.accept(ModItems.CELESTIAL_SHARD.get());
                        output.accept(ModItems.EMPTY_VESTIGE.get());
                        output.accept(ModItems.POWER_VESTIGE.get());
                        output.accept(ModItems.ELEMENTAL_VESTIGE.get());
                        output.accept(ModItems.LIFE_VESTIGE.get());
                        output.accept(ModItems.MOTION_VESTIGE.get());
                        output.accept(ModItems.RUIN_VESTIGE.get());
                        output.accept(ModItems.SPACE_VESTIGE.get());
                        output.accept(ModItems.MIND_VESTIGE.get());
                        output.accept(ModItems.REFINED_CORE.get());
                        output.accept(ModItems.PERFECTED_CORE.get());
                        output.accept(ModItems.SEVERANCE_STONE.get());

                        // Blocks (using the items registered in ModBlocks)
                        output.accept(ModBlocks.CELESTIAL_ORE_ITEM.get());
                        output.accept(ModBlocks.DEEPSLATE_CELESTIAL_ORE_ITEM.get());
                        output.accept(ModBlocks.CELESTIAL_BLOCK_ITEM.get());
                        output.accept(ModBlocks.THORN_VINE_ITEM.get());
                        output.accept(ModBlocks.ICE_SPIKE_ITEM.get());
                        output.accept(ModBlocks.CASINO_BARS_ITEM.get());
                        output.accept(ModBlocks.CASINO_FLOOR_ITEM.get());

                        // Misc
                        output.accept(ModItems.WINGS_OF_VALOR.get());
                    })
                    .build()
            );

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}