package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Every so often, things just... work out for you. */
public final class BlessingLuck extends Effect {
    private static final int INTERVAL_TICKS = 400;
    private static final int XP_PER_PULSE = 3;

    public BlessingLuck() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.RABBIT_FOOT);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.giveExperiencePoints(XP_PER_PULSE);
        }
    }
}
