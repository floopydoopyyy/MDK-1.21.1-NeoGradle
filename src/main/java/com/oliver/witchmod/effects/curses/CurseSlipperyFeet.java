package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** No traction whatsoever. You go where your feet decide to go. */
public final class CurseSlipperyFeet extends Effect {
    private static final int INTERVAL_TICKS = 60;
    private static final double NUDGE = 0.25;

    public CurseSlipperyFeet() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.ICE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && target.onGround()) {
            var random = target.getRandom();
            double dx = (random.nextDouble() - 0.5) * NUDGE;
            double dz = (random.nextDouble() - 0.5) * NUDGE;
            target.push(dx, 0, dz);
        }
    }
}
