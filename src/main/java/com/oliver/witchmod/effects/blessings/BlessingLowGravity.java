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
 * Gravity takes it easy on you — big hops, soft landings.
 *
 * <p>Jump Boost + Slow Falling is a genuinely close vanilla realization of the spec's higher-jump /
 * slower-fall / reduced-fall-damage numbers; a later pass can swap to exact attribute multipliers if the
 * feel needs tuning.
 */
public final class BlessingLowGravity extends Effect {
    public BlessingLowGravity() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.ENDER_EYE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.JUMP, durationTicks, 1);
        EffectUtil.addTimedEffect(target, MobEffects.SLOW_FALLING, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.JUMP);
        EffectUtil.removeTimedEffect(target, MobEffects.SLOW_FALLING);
    }
}
