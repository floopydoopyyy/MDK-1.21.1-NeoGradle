package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.EventCategory;

/** You're briefly, inexplicably, wrapped in wool. It's surprisingly restrictive. */
public final class NeutralWooliam extends BewitchmentEvent {
    public NeutralWooliam() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        EffectUtil.addTimedEffect(initiator, MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 2);
        level.sendParticles(ParticleTypes.POOF, initiator.getX(), initiator.getY() + 1.0, initiator.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
    }

    @Override
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {
        if (target != null) {
            EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SLOWDOWN);
        }
    }
}
