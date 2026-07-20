package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Right when it looks like a bad fall, something intervenes. */
public final class BlessingTwistOfFate extends Effect {
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final float FALL_DISTANCE_THRESHOLD = 3.0F;
    private static final int CUSHION_TICKS = 20;

    public BlessingTwistOfFate() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.NETHER_STAR);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, CHECK_INTERVAL_TICKS)
                && target.fallDistance > FALL_DISTANCE_THRESHOLD
                && !target.hasEffect(MobEffects.SLOW_FALLING)) {
            EffectUtil.addTimedEffect(target, MobEffects.SLOW_FALLING, CUSHION_TICKS, 0);
        }
    }
}
