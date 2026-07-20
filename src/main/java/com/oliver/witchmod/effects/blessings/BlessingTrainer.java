package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Someone's coaching you along. Small, steady gains. */
public final class BlessingTrainer extends Effect {
    private static final int INTERVAL_TICKS = 200;
    private static final int XP_PER_PULSE = 1;

    public BlessingTrainer() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.giveExperiencePoints(XP_PER_PULSE);
        }
    }
}
