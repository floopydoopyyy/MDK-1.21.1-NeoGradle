package com.oliver.witchmod.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/**
 * The Effect Registry (curses + blessings) and the Event Registry (neutrals + globals) — see
 * CLAUDE.md section 10, Phase 0. Kept as two separate registries since curses/blessings share the
 * Sacrificial Item pool and discovery/backfire/modifier machinery, while neutrals/globals share the
 * "no direct counterplay" design; the two groups don't otherwise interact.
 *
 * <p>Synced to clients so the Compendium (Phase 5) can list every registered effect/event by id without
 * needing server-authoritative lookups for undiscovered "rumour" entries.
 */
public final class WitchModRegistries {
    public static final ResourceKey<Registry<Effect>> EFFECT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "effects"));

    public static final ResourceKey<Registry<BewitchmentEvent>> EVENT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "events"));

    public static final DeferredRegister<Effect> EFFECTS = DeferredRegister.create(EFFECT_REGISTRY_KEY, WitchMod.MODID);
    public static final DeferredRegister<BewitchmentEvent> EVENTS = DeferredRegister.create(EVENT_REGISTRY_KEY, WitchMod.MODID);

    public static final Registry<Effect> EFFECT_REGISTRY = EFFECTS.makeRegistry(builder -> builder.sync(true));
    public static final Registry<BewitchmentEvent> EVENT_REGISTRY = EVENTS.makeRegistry(builder -> builder.sync(true));

    private WitchModRegistries() {}

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
        EVENTS.register(modEventBus);
    }
}
