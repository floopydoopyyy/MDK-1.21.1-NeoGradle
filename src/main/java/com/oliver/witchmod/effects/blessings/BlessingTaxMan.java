package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** A refund shows up. Regularly. No explanation given. */
public final class BlessingTaxMan extends Effect {
    private static final int INTERVAL_TICKS = 1200;

    public BlessingTaxMan() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.GOLD_INGOT);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.giveExperienceLevels(1);
        }
    }
}
