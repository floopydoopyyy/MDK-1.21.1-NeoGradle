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
 * The fish practically leap onto your hook.
 *
 * <p>PROTOTYPE: the full spec shortens fishing bite time and bumps the treasure chance, which means hooking
 * the fishing-hook wait timer and loot roll. As a close vanilla-backed stand-in this grants Luck while
 * active (which already improves fishing loot quality); the faster-bite timing is deferred.
 */
public final class BlessingAngler extends Effect {
    public BlessingAngler() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.SALMON);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.LUCK, durationTicks, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.LUCK);
    }
}
