package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Every so often, at night, it catches up with you all at once. */
public final class CurseInsomniac extends Effect {
    private static final int INTERVAL_TICKS = 6000;
    private static final int BURST_TICKS = 100;

    public CurseInsomniac() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.PHANTOM_MEMBRANE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && level.isNight()) {
            EffectUtil.addTimedEffect(target, MobEffects.CONFUSION, BURST_TICKS, 0);
            EffectUtil.addTimedEffect(target, MobEffects.DARKNESS, BURST_TICKS, 0);
        }
    }
}
