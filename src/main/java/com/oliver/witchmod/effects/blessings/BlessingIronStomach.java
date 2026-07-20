package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You could eat anything right now and be fine. */
public final class BlessingIronStomach extends Effect {
    private static final int INTERVAL_TICKS = 20;

    public BlessingIronStomach() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.ROTTEN_FLESH);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && target.hasEffect(MobEffects.HUNGER)) {
            target.removeEffect(MobEffects.HUNGER);
        }
    }
}
