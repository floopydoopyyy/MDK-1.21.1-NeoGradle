package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** It's like you've got backup. Extra padding you didn't have to earn. */
public final class BlessingArmy extends Effect {
    private static final int INTERVAL_TICKS = 200;
    private static final int ABSORPTION_TICKS = 260;

    public BlessingArmy() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.SHIELD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            EffectUtil.addTimedEffect(target, MobEffects.ABSORPTION, ABSORPTION_TICKS, 1);
        }
    }
}
