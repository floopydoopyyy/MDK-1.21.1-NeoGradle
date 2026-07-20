package com.oliver.witchmod.effects.curses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** A faint, harmless shimmer follows you around. Everyone can see something's off. */
public final class CurseAura extends Effect {
    private static final int INTERVAL_TICKS = 10;

    public CurseAura() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.NOTE_BLOCK);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.4, 0.6, 0.4, 0.0);
        }
    }
}
