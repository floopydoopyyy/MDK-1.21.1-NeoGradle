package com.loopy.loopypowers.item;

import com.loopy.loopypowers.Loopypowers;
import com.loopy.loopypowers.block.ModBlocks; // We use the ITEMS register from here
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Loopypowers.MOD_ID);

    public static final DeferredItem<Item> WINGS_OF_VALOR = ITEMS.register("wings_of_valor",
            () -> new WingsOfValorItem(new Item.Properties().stacksTo(1).durability(432))
    );

    // CELESTIAL SHARD
    public static final DeferredItem<Item> CELESTIAL_SHARD = ModBlocks.ITEMS.register("celestial_shard",
            () -> new CelestialShardItem(new Item.Properties())
    );

    // RITUAL VESTIGES
    public static final DeferredItem<Item> POWER_VESTIGE = ModBlocks.ITEMS.register("power_vestige",
            () -> new PowerVestigeItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> ELEMENTAL_VESTIGE = ModBlocks.ITEMS.register("elemental_vestige",
            () -> new ElementalVestigeItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> LIFE_VESTIGE = ModBlocks.ITEMS.register("life_vestige",
            () -> new LifeVestigeItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> RUIN_VESTIGE = ModBlocks.ITEMS.register("ruin_vestige",
            () -> new RuinVestigeItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> MOTION_VESTIGE = ModBlocks.ITEMS.register("motion_vestige",
            () -> new MotionVestigeItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> SPACE_VESTIGE = ModBlocks.ITEMS.register("space_vestige",
            () -> new SpaceVestigeItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> MIND_VESTIGE = ModBlocks.ITEMS.register("mind_vestige",
            () -> new MindVestigeItem(new Item.Properties().stacksTo(1))
    );

    // EMPTY VESTIGE
    public static final DeferredItem<Item> EMPTY_VESTIGE = ModBlocks.ITEMS.register("empty_vestige",
            () -> new EmptyVestigeItem(new Item.Properties())
    );

    // SEVERANCE STONE
    public static final DeferredItem<Item> SEVERANCE_STONE = ModBlocks.ITEMS.register("severance_stone",
            () -> new SeveranceStoneItem(new Item.Properties())
    );

    // CORES
    public static final DeferredItem<Item> REFINED_CORE = ModBlocks.ITEMS.register("refined_celestial_core",
            () -> new RefinedCoreItem(new Item.Properties())
    );
    public static final DeferredItem<Item> PERFECTED_CORE = ModBlocks.ITEMS.register("perfected_celestial_core",
            () -> new PerfectedCoreItem(new Item.Properties())
    );

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus); // This is the line that actually connects your items to the game
    }
}