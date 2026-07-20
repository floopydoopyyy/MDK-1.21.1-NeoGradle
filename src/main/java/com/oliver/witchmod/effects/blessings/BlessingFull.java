package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You just never seem to get hungry. */
public final class BlessingFull extends Effect {
    private static final int INTERVAL_TICKS = 100;

    public BlessingFull() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.BREAD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.getFoodData().setFoodLevel(20);
            target.getFoodData().setSaturation(20.0F);
        }
    }
}
