package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You'll be walking in a straight line, and then you just... won't be. */
public final class CurseStickDrift extends Effect {
    private static final int INTERVAL_TICKS = 80;
    private static final double NUDGE = 0.15;

    public CurseStickDrift() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.FISHING_ROD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            var random = target.getRandom();
            double dx = (random.nextDouble() - 0.5) * NUDGE;
            double dz = (random.nextDouble() - 0.5) * NUDGE;
            target.push(dx, 0, dz);
        }
    }
}
