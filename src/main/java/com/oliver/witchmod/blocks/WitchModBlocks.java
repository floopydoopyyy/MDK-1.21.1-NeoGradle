package com.oliver.witchmod.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** Registers all blocks: Amethyst Bell (Phase 3, section 3) and the Phase 4 blocks (section 2). */
public final class WitchModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchMod.MODID);

    public static final DeferredBlock<AmethystBellBlock> AMETHYST_BELL = BLOCKS.registerBlock("amethyst_bell",
            AmethystBellBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noOcclusion().strength(2.5F));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> AMETHYST_BELL_ITEM = ITEMS.registerSimpleBlockItem(AMETHYST_BELL);

    /** Storage block for Cursed Essence (CLAUDE.md section 2.2); also the Global bank's currency unit. */
    public static final DeferredBlock<Block> CURSED_ESSENCE_BLOCK = BLOCKS.registerSimpleBlock("cursed_essence_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CURSED_ESSENCE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(CURSED_ESSENCE_BLOCK);

    /**
     * Radius-based curse/blessing shield (CLAUDE.md section 2.4). No recipe specified in the doc yet —
     * command/creative only until one's decided (see Human Action Items).
     */
    public static final DeferredBlock<WardingTotemBlock> WARDING_TOTEM = BLOCKS.registerBlock("warding_totem",
            WardingTotemBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WARDING_TOTEM_ITEM = ITEMS.registerSimpleBlockItem(WARDING_TOTEM);

    /**
     * The ritual block (CLAUDE.md section 2.1). No real UI until Phase 5 — right-click with an item
     * inserts it into the matching slot, right-click empty-handed casts, sneak + right-click empty-handed
     * returns the contents.
     */
    public static final DeferredBlock<BewitchingTableBlock> BEWITCHING_TABLE = BLOCKS.registerBlock("bewitching_table",
            BewitchingTableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4.0F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BEWITCHING_TABLE_ITEM = ITEMS.registerSimpleBlockItem(BEWITCHING_TABLE);

    /**
     * Read-only book UI, accessed via a lectern-style block (CLAUDE.md section 2.3). Recipe: Wood +
     * Compendium.
     */
    public static final DeferredBlock<LedgerBlock> LEDGER = BLOCKS.registerBlock("ledger",
            LedgerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LEDGER_ITEM = ITEMS.registerSimpleBlockItem(LEDGER);

    private WitchModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
