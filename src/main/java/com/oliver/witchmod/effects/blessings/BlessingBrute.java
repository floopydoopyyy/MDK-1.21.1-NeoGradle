package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * Build up a head of steam and nothing stands in your way. Preferably literally.
 *
 * <p>PROTOTYPE: the full spec ramps speed on a straight-line sprint until, at cap, collisions launch
 * entities and you smash through blocks at an HP cost — plus custom ramp/smash sounds (Section 12). As a
 * loosely-functional stand-in this simply grants steady Strength + Speed while active. The ramp mechanic,
 * entity launch, block-smashing, and sounds are deferred.
 */
public final class BlessingBrute extends Effect {
    public BlessingBrute() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.IRON_HELMET);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.DAMAGE_BOOST, durationTicks, 0);
        EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SPEED, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.DAMAGE_BOOST);
        EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SPEED);
    }
}
