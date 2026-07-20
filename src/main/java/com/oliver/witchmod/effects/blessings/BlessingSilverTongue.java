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
 * Every villager suddenly thinks you're their best customer.
 *
 * <p>PROTOTYPE: the full spec floors ALL trade prices to the vanilla minimum. As a close vanilla-backed
 * stand-in this applies Hero of the Village (which already discounts trades, though not all the way to the
 * floor) for the duration. The exact price-flooring is deferred.
 */
public final class BlessingSilverTongue extends Effect {
    public BlessingSilverTongue() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.EMERALD_BLOCK);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.HERO_OF_THE_VILLAGE, durationTicks, 4);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.HERO_OF_THE_VILLAGE);
    }
}
