package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Everything just seems to click a little faster for you right now. */
public final class BlessingStudious extends Effect {
    private static final int INTERVAL_TICKS = 300;
    private static final int XP_PER_PULSE = 4;

    public BlessingStudious() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.ENCHANTED_BOOK);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.giveExperiencePoints(XP_PER_PULSE);
        }
    }
}
