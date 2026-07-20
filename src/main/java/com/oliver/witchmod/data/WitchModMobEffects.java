package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/**
 * The three wrapper status effects from CLAUDE.md section 2.6. Purely informational — they carry no
 * mechanical behavior of their own; {@link StatusEffectSync} keeps their duration matched to whatever
 * curses/blessings/afflictions are actually active.
 */
public final class WitchModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, WitchMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> CURSED =
            MOB_EFFECTS.register("cursed", () -> new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8B00FF));
    public static final DeferredHolder<MobEffect, MobEffect> BLESSED =
            MOB_EFFECTS.register("blessed", () -> new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD700));
    public static final DeferredHolder<MobEffect, MobEffect> AFFLICTED =
            MOB_EFFECTS.register("afflicted", () -> new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x800080));

    private WitchModMobEffects() {}

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }

    /** {@link MobEffect}'s constructor is protected; this just exposes it. */
    private static final class SimpleMobEffect extends MobEffect {
        SimpleMobEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
