package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** The ground remembers being lava sometimes, and so do your feet. */
public final class CurseFloorIsLava extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final float BURN_SECONDS = 2.0F;

    public CurseFloorIsLava() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 55, () -> Items.MAGMA_BLOCK);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && target.onGround()) {
            target.igniteForSeconds(BURN_SECONDS);
        }
    }
}
