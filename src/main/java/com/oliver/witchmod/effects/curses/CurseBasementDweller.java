package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You never go outside. Daylight does not agree with you. */
public final class CurseBasementDweller extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final int BURST_TICKS = 60;

    public CurseBasementDweller() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 40, () -> Items.COBBLESTONE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && !level.isNight() && level.canSeeSky(target.blockPosition())) {
            EffectUtil.addTimedEffect(target, MobEffects.WEAKNESS, BURST_TICKS, 0);
        }
    }
}
