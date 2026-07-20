package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * The water is calling, and staying dry aches. Get back in.
 *
 * <p>PROTOTYPE: the full spec builds a "longing" stat with staged mining-fatigue/slowness/physical-pull
 * toward the nearest water. This stand-in reproduces the first two stages loosely — while out of water the
 * victim suffers mining fatigue (and slowness once the longing would be high); submerging clears it. The
 * physical water-pull and passive Drowned behavior are deferred.
 */
public final class CurseSirensCall extends Effect {
    private static final int INTERVAL_TICKS = 40;
    private static final int EFFECT_TICKS = 60;

    public CurseSirensCall() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 35, () -> Items.HEART_OF_THE_SEA);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        if (target.isInWater()) {
            EffectUtil.removeTimedEffect(target, MobEffects.DIG_SLOWDOWN);
            EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN);
        } else {
            EffectUtil.addTimedEffect(target, MobEffects.DIG_SLOWDOWN, EFFECT_TICKS, 0);
            EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 0);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.DIG_SLOWDOWN);
        EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN);
    }
}
