package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You're never really alone right now — something friendly trails just behind you. */
public final class BlessingCompany extends Effect {
    private static final int INTERVAL_TICKS = 20;

    public BlessingCompany() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.BONE_MEAL);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            double angle = (ticksRemaining % 100) / 100.0 * Math.PI * 2;
            double x = target.getX() - Math.cos(angle) * 1.5;
            double z = target.getZ() - Math.sin(angle) * 1.5;
            level.sendParticles(ParticleTypes.HEART, x, target.getY() + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
