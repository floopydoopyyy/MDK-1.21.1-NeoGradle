package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You never learned to swim properly, and the water knows it. */
public final class CurseBadSwimmer extends Effect {
    private static final int INTERVAL_TICKS = 40;
    private static final int BURST_TICKS = 60;

    public CurseBadSwimmer() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.IRON_INGOT);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && target.isInWater()) {
            EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN, BURST_TICKS, 1);
        }
    }
}
