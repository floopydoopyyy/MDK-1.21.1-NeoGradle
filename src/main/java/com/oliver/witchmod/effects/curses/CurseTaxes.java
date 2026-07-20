package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** The tax man cometh. Regularly, and without warning. */
public final class CurseTaxes extends Effect {
    private static final int INTERVAL_TICKS = 1200;

    public CurseTaxes() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 80, () -> Items.EMERALD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && target.experienceLevel > 0) {
            target.giveExperienceLevels(-1);
        }
    }
}
