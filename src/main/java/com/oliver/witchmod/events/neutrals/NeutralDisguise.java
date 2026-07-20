package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.EventCategory;

/** For a little while, nobody can quite make you out. */
public final class NeutralDisguise extends BewitchmentEvent {
    public NeutralDisguise() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator != null) {
            EffectUtil.addTimedEffect(initiator, MobEffects.INVISIBILITY, durationTicks, 0);
        }
    }

    @Override
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {
        if (target != null) {
            EffectUtil.removeTimedEffect(target, MobEffects.INVISIBILITY);
        }
    }
}
