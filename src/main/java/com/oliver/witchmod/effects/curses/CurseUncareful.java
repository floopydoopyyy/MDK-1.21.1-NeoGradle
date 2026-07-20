package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You just weren't paying attention. Again. */
public final class CurseUncareful extends Effect {
    private static final int INTERVAL_TICKS = 300;
    private static final float TRIGGER_CHANCE = 0.15F;

    public CurseUncareful() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 40, () -> Items.FLINT);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && EffectUtil.chance(target.getRandom(), TRIGGER_CHANCE)) {
            ServerLevel level = target.serverLevel();
            target.hurt(level.damageSources().generic(), 2.0F);
        }
    }
}
