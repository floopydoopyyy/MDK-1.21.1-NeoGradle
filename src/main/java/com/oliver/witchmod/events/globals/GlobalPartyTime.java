package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.EventCategory;

/** It's a party. Everyone online is invited, whether they wanted one or not. */
public final class GlobalPartyTime extends BewitchmentEvent {
    public GlobalPartyTime() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.addTimedEffect(player, MobEffects.JUMP, durationTicks, 1);
            EffectUtil.addTimedEffect(player, MobEffects.MOVEMENT_SPEED, durationTicks, 0);
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.5, 0.5, 0.5, 0.05);
        }
    }

    @Override
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.removeTimedEffect(player, MobEffects.JUMP);
            EffectUtil.removeTimedEffect(player, MobEffects.MOVEMENT_SPEED);
        }
    }
}
