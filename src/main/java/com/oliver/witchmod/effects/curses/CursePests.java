package com.oliver.witchmod.effects.curses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Bugs. Always a few bugs on you, no matter where you go. */
public final class CursePests extends Effect {
    private static final int INTERVAL_TICKS = 300;

    public CursePests() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.STONE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, target.getX(), target.getY() + 1.8, target.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
            target.hurt(level.damageSources().generic(), 0.5F);
        }
    }
}
