package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You come alive at night. Faster, sharper, once the sun's down. */
public final class BlessingNightowl extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final int BURST_TICKS = 120;

    public BlessingNightowl() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.GLOW_BERRIES);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && level.isNight()) {
            EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SPEED, BURST_TICKS, 0);
        }
    }
}
