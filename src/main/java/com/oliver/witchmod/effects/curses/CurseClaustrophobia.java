package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Walls feel a little too close in here. The sky helps — being shut in does not. Milder than Basement Dweller. */
public final class CurseClaustrophobia extends Effect {
    private static final int INTERVAL_TICKS = 40;
    private static final float DAMAGE = 0.5F;

    public CurseClaustrophobia() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.COBBLED_DEEPSLATE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && !level.canSeeSky(target.blockPosition())) {
            target.hurt(level.damageSources().generic(), DAMAGE);
        }
    }
}
