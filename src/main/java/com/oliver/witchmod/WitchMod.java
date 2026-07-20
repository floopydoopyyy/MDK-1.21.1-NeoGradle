package com.oliver.witchmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModMobEffects;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.events.Globals;
import com.oliver.witchmod.events.Neutrals;
import com.oliver.witchmod.blocks.WardingTotemBlock;
import com.oliver.witchmod.blocks.WitchModBlockEntities;
import com.oliver.witchmod.blocks.WitchModBlocks;
import com.oliver.witchmod.blocks.WitchModFluids;
import com.oliver.witchmod.items.ItemWard;
import com.oliver.witchmod.items.WitchModItems;
import com.oliver.witchmod.ui.WitchModMenus;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(WitchMod.MODID)
public class WitchMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "witchmod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "witchmod" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "witchmod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "witchmod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "witchmod:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "witchmod:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "witchmod:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // The mod's creative tab — every WitchMod item and block, iconed by Cursed Essence (the mod's currency).
    // (Not specified in CLAUDE.md; added on request.)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WITCHMOD_TAB = CREATIVE_MODE_TABS.register("witchmod", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.witchmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> WitchModItems.CURSED_ESSENCE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Items (section 4)
                output.accept(WitchModItems.CURSED_ESSENCE.get());
                output.accept(WitchModItems.PLAYER_ESSENCE.get());
                output.accept(WitchModItems.COMPENDIUM.get());
                output.accept(WitchModItems.VOODOO_DOLL.get());
                output.accept(WitchModItems.NEEDLE.get());
                output.accept(WitchModItems.WARD.get());
                output.accept(WitchModItems.SCRYING_MIRROR.get());
                output.accept(WitchModItems.EFFIGY.get());
                output.accept(WitchModItems.CURSED_COIN.get());
                output.accept(WitchModItems.BLESSED_COIN.get());
                output.accept(WitchModItems.EXECUTIONERS_COIN.get());
                output.accept(WitchModItems.JAR.get());
                output.accept(WitchModItems.CURSED_JAR.get());
                output.accept(WitchModItems.RECOVERY_COMPASS.get());
                // Blocks (section 3)
                output.accept(WitchModBlocks.BEWITCHING_TABLE_ITEM.get());
                output.accept(WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get());
                output.accept(WitchModBlocks.LEDGER_ITEM.get());
                output.accept(WitchModBlocks.WARDING_TOTEM_ITEM.get());
                output.accept(WitchModBlocks.AMETHYST_BELL_ITEM.get());
                // Purifying Water bucket (section 2.5)
                output.accept(WitchModFluids.PURIFYING_WATER_BUCKET.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public WitchMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register the Effect and Event registries (curses/blessings, neutrals/globals)
        WitchModRegistries.register(modEventBus);
        // Register the per-player active-effect data attachment
        WitchModAttachments.register(modEventBus);
        // Register the Cursed/Blessed/Afflicted wrapper status effects
        WitchModMobEffects.register(modEventBus);
        // Register custom item data components (bound player, captured effects)
        WitchModDataComponents.register(modEventBus);
        // Register this mod's items and blocks (Phase 3/section 3)
        WitchModItems.register(modEventBus);
        WitchModBlocks.register(modEventBus);
        WitchModFluids.register(modEventBus);
        WitchModBlockEntities.register(modEventBus);
        WitchModMenus.register(modEventBus);

        // Force curse/blessing/event holder classes to load so their DeferredRegister entries exist
        // before RegisterEvent fires
        Curses.bootstrap();
        Blessings.bootstrap();
        Neutrals.bootstrap();
        Globals.bootstrap();

        // Let the Ward item and Warding Totem block hook into effect application without EffectManager
        // depending on them directly
        EffectManager.setWardHook(ItemWard::hasActiveWard, ItemWard::onDeflect);
        EffectManager.setTotemHook(WardingTotemBlock::isProtected);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // SERVER (not COMMON) — every value here affects gameplay resolution the client must agree with
        // (the Table screen's live probability preview reads the same formula constants/overrides the
        // server uses to actually resolve a cast), and SERVER configs are per-world and auto-synced to
        // clients on join, unlike COMMON. No custom payload needed for that sync — same "use the built-in
        // mechanism instead of a bespoke one" approach as the Table's Cast button (see BewitchingTableMenu).
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("WitchMod common setup complete");
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
