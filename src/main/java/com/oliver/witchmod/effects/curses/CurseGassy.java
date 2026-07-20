package com.oliver.witchmod.effects.curses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** It happens. Regularly. Audibly. */
public final class CurseGassy extends Effect {
    private static final int INTERVAL_TICKS = 160;

    public CurseGassy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.PUFFERFISH);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.5, target.getZ(), 6, 0.2, 0.1, 0.2, 0.01);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 0.8F);
        }
    }
}
