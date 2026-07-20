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
 * The more you dig, the faster you dig. Momentum in stone.
 *
 * <p>PROTOTYPE: the full spec ramps Haste up as you keep mining (stacking to IV, decaying after a grace
 * period). As a loosely-functional stand-in this simply grants a flat Haste while active; the mining-driven
 * stack/decay is deferred.
 */
public final class BlessingExcavation extends Effect {
    public BlessingExcavation() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.IRON_PICKAXE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.DIG_SPEED, durationTicks, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.DIG_SPEED);
    }
}
