package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Something's watching your back. Hits just don't land as often, or as hard. */
public final class BlessingBodyguard extends Effect {
    public BlessingBodyguard() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.BONE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.DAMAGE_RESISTANCE, durationTicks, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.DAMAGE_RESISTANCE);
    }
}
