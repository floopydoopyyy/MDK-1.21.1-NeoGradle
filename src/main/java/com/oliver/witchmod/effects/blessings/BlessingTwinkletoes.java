package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Light on your feet — falls don't bother you, and you're quicker for it. */
public final class BlessingTwinkletoes extends Effect {
    public BlessingTwinkletoes() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.HAY_BLOCK);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.SLOW_FALLING, durationTicks, 0);
        EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SPEED, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.SLOW_FALLING);
        EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SPEED);
    }
}
