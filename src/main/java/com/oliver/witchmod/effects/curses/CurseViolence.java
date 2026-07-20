package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Sudden, unprovoked violent urges — a shove of pain every so often, for no reason at all. */
public final class CurseViolence extends Effect {
    private static final int INTERVAL_TICKS = 200;

    public CurseViolence() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 45, () -> Items.IRON_SWORD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            target.hurt(level.damageSources().magic(), 2.0F);
        }
    }
}
