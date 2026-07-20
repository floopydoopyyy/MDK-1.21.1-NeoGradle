package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Parched, and there's nothing you can do about it but drink (master-spec Thirst Meter — a FULLY-FUNCTIONAL
 * custom HUD, Phase D). Drives the auto-synced {@link WitchModAttachments#THIRST} attachment which the
 * client-side {@code ThirstHudLayer} renders as a droplet bar above hunger.
 *
 * <p>Refill: drinking a Water Bottle (+{@value #BOTTLE_RESTORE}, handled in {@code CurseEventHandler}) or
 * standing in water (slow passive sip, with a chance of a bad-water debuff). PROTOTYPE simplification: the
 * spec's "right-click a water source" is realized here as the passive in-water sip, since raw water isn't a
 * right-clickable block. Empties to Slowness+Weakness (no damage).
 *
 * <p>Sacrificial item stays Glass Bottle (prototype) — the spec's "Water Bottle" can't be isolated under
 * exact-item matching (all potions are {@code Items.POTION}); see the Section 11 soft flag.
 */
public final class CurseThirstMeter extends Effect {
    public static final int THIRST_MAX = 20;
    public static final int BOTTLE_RESTORE = 6;
    private static final int DRAIN_INTERVAL_TICKS = 200;   // lose 1 per 10s while dry
    private static final int SIP_INTERVAL_TICKS = 40;      // gain 1 per 2s while in water
    private static final float RAW_WATER_DEBUFF_CHANCE = 0.25F;
    private static final int EMPTY_EFFECT_INTERVAL = 40;
    private static final int EMPTY_EFFECT_TICKS = 60;

    public CurseThirstMeter() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.GLASS_BOTTLE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.THIRST, THIRST_MAX);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.THIRST, -1); // hide the bar
        EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN);
        EffectUtil.removeTimedEffect(target, MobEffects.WEAKNESS);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int thirst = target.getData(WitchModAttachments.THIRST);
        if (thirst < 0) {
            thirst = THIRST_MAX; // self-heal (e.g. after relog, since onApply doesn't re-run)
        }

        if (target.isInWater()) {
            if (EffectUtil.every(ticksRemaining, SIP_INTERVAL_TICKS) && thirst < THIRST_MAX) {
                thirst++;
                if (EffectUtil.chance(target.getRandom(), RAW_WATER_DEBUFF_CHANCE)) {
                    EffectUtil.addTimedEffect(target, MobEffects.HUNGER, 100, 0);
                }
            }
        } else if (EffectUtil.every(ticksRemaining, DRAIN_INTERVAL_TICKS) && thirst > 0) {
            thirst--;
        }
        target.setData(WitchModAttachments.THIRST, thirst);

        if (thirst == 0 && EffectUtil.every(ticksRemaining, EMPTY_EFFECT_INTERVAL)) {
            EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN, EMPTY_EFFECT_TICKS, 0);
            EffectUtil.addTimedEffect(target, MobEffects.WEAKNESS, EMPTY_EFFECT_TICKS, 0);
        }
    }

    /** Adds thirst (clamped), but only while the curse is active ({@code thirst >= 0}). Used by refill hooks. */
    public static void addThirst(ServerPlayer player, int delta) {
        int thirst = player.getData(WitchModAttachments.THIRST);
        if (thirst < 0) {
            return;
        }
        player.setData(WitchModAttachments.THIRST, Math.min(THIRST_MAX, thirst + delta));
    }
}
