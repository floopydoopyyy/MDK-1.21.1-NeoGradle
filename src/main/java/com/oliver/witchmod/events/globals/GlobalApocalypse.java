package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.EventCategory;

/** The end of the world. A brief, server-wide one. */
public final class GlobalApocalypse extends BewitchmentEvent {
    public GlobalApocalypse() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.addTimedEffect(player, MobEffects.WITHER, durationTicks, 0);
            EffectUtil.addTimedEffect(player, MobEffects.DARKNESS, durationTicks, 0);
        }
    }

    @Override
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.removeTimedEffect(player, MobEffects.WITHER);
            EffectUtil.removeTimedEffect(player, MobEffects.DARKNESS);
        }
    }
}
