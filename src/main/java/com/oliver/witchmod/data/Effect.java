package com.oliver.witchmod.data;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * A single curse or blessing. There are no strength tiers (CLAUDE.md section 1) — every effect is one
 * fixed behavior, hooked in here for Phase 1 to implement per curse/blessing. Registered through
 * {@link WitchModRegistries#EFFECTS}.
 */
public abstract class Effect {
    private final EffectCategory category;
    private final EffectCostTier tier;
    private final int baseCost;
    private final Supplier<? extends Item> sacrificialItem;

    protected Effect(EffectCategory category, EffectCostTier tier, int baseCost, Supplier<? extends Item> sacrificialItem) {
        this.category = category;
        this.tier = tier;
        this.baseCost = baseCost;
        this.sacrificialItem = sacrificialItem;
    }

    public EffectCategory category() {
        return category;
    }

    public EffectCostTier tier() {
        return tier;
    }

    /** Essence cost consumed by the success/backfire formulas in CLAUDE.md section 5.7. */
    public int baseCost() {
        return baseCost;
    }

    /** The item that must be placed in the Bewitching Table's Sacrificial Item slot to select this effect. */
    public Item sacrificialItem() {
        return sacrificialItem.get();
    }

    /**
     * Called when this effect begins acting on {@code target}, whether by successful Table cast,
     * command, Voodoo Doll forward, Coin gamble, or backfire (Mirror).
     *
     * @param caster       null when the effect wasn't attributed to a specific player (e.g. command with
     *                     no selector, or a system-triggered backfire).
     * @param durationTicks the tracked duration this instance was applied for; effects that layer a
     *                     vanilla {@link net.minecraft.world.effect.MobEffectInstance} on top should size
     *                     it to match, since removal is explicit (see {@link #onRemove}) rather than relying
     *                     on the vanilla effect's own countdown.
     */
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {}

    /** Called when this effect stops acting on {@code target}, whether by expiry, cure, or removal. */
    public void onRemove(ServerPlayer target) {}

    /**
     * Called once per tick while this effect is active on {@code target}. Most effects that only need a
     * one-time state change (a status effect, an attribute modifier) can ignore this; effects with
     * periodic behavior (a chance each tick, a fixed interval) should check {@code ticksRemaining} here.
     */
    public void onTick(ServerPlayer target, int ticksRemaining) {}
}
