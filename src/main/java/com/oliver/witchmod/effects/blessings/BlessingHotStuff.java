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
 * You run hot. Furnaces near you feel the heat and work faster.
 *
 * <p>PROTOTYPE: the full spec speeds up nearby furnace/blast-furnace/smoker/campfire smelting, which means
 * bumping those block entities' cook progress (protected internals — needs an accessor pass). As a
 * low-risk stand-in this grants Fire Resistance while active, thematically "hot stuff," so the blessing is
 * testable now. The actual smelting-speed boost is deferred.
 */
public final class BlessingHotStuff extends Effect {
    public BlessingHotStuff() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.COAL);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.FIRE_RESISTANCE, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.FIRE_RESISTANCE);
    }
}
