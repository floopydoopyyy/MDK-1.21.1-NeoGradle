package com.loopy.loopypowers.block;

import com.loopy.loopypowers.Loopypowers;
import com.loopy.loopypowers.item.CelestialBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    // 1. Create DeferredRegisters for both Blocks and their corresponding Items
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Loopypowers.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Loopypowers.MOD_ID);

    // THORN VINE
    public static final DeferredBlock<ThornVineBlock> THORN_VINE = BLOCKS.register("thorn_vine",
            () -> new ThornVineBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.COBWEB)
                            .strength(2.0f, 120.0f)               // time to break / blast resistance
                            .sound(SoundType.GRASS)               // sound
                            .noCollission()                       // cam walk through
                            .noOcclusion()                        // nonOpaque
            )
    );
    // NeoForge has a helper method to easily register the item form of a block!
    public static final DeferredItem<BlockItem> THORN_VINE_ITEM = ITEMS.registerSimpleBlockItem("thorn_vine", THORN_VINE);


    // ICE SPIKE
    public static final DeferredBlock<IceSpikeBlock> ICE_SPIKE = BLOCKS.register("ice_spike",
            () -> new IceSpikeBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.POINTED_DRIPSTONE)
                            .lightLevel(state -> 6)
                            .strength(1.0f)
                            .sound(SoundType.GLASS)
                            .randomTicks()
            )
    );
    public static final DeferredItem<BlockItem> ICE_SPIKE_ITEM = ITEMS.registerSimpleBlockItem("ice_spike", ICE_SPIKE);


    // CASINO BARS
    public static final DeferredBlock<CasinoBarsBlock> CASINO_BARS = BLOCKS.register("casino_bars",
            () -> new CasinoBarsBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS)
                            .strength(35.0f, 1200.0f)
                            .sound(SoundType.METAL)
            )
    );
    public static final DeferredItem<BlockItem> CASINO_BARS_ITEM = ITEMS.registerSimpleBlockItem("casino_bars", CASINO_BARS);


    // CASINO FLOOR
    public static final DeferredBlock<CasinoFloorBlock> CASINO_FLOOR = BLOCKS.register("casino_floor",
            () -> new CasinoFloorBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                            .strength(25.0f, 1200.0f)
                            .sound(SoundType.STONE)
            )
    );
    public static final DeferredItem<BlockItem> CASINO_FLOOR_ITEM = ITEMS.registerSimpleBlockItem("casino_floor", CASINO_FLOOR);


    // CELESTIAL ORE
    public static final DeferredBlock<CelestialOreBlock> CELESTIAL_ORE = BLOCKS.register("celestial_ore",
            () -> new CelestialOreBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE)
                            .strength(5.0f, 6.0f)
                            .requiresCorrectToolForDrops() // requiresTool()
                            .lightLevel(state -> 12)
            )
    );
    public static final DeferredItem<BlockItem> CELESTIAL_ORE_ITEM = ITEMS.registerSimpleBlockItem("celestial_ore", CELESTIAL_ORE);


    // DEEPSLATE CELESTIAL ORE
    public static final DeferredBlock<CelestialOreBlock> DEEPSLATE_CELESTIAL_ORE = BLOCKS.register("deepslate_celestial_ore",
            () -> new CelestialOreBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE)
                            .strength(5.5f, 7.0f)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 12)
            )
    );
    public static final DeferredItem<BlockItem> DEEPSLATE_CELESTIAL_ORE_ITEM = ITEMS.registerSimpleBlockItem("deepslate_celestial_ore", DEEPSLATE_CELESTIAL_ORE);


    // CELESTIAL BLOCK
    public static final DeferredBlock<CelestialBlock> CELESTIAL_BLOCK = BLOCKS.register("celestial_block",
            () -> new CelestialBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.NETHERITE_BLOCK)
                            .strength(5.0f, 6.0f)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 12)
            )
    );

    public static final DeferredItem<CelestialBlockItem> CELESTIAL_BLOCK_ITEM = ITEMS.register("celestial_block",
            () -> new CelestialBlockItem(CELESTIAL_BLOCK.get(), new Item.Properties())
    );


    // Expose a method to attach the DeferredRegisters to the main Event Bus
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}