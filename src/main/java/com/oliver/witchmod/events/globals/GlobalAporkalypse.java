package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.EventCategory;

/** A smaller, sillier apocalypse. Everyone gets ravenous. */
public final class GlobalAporkalypse extends BewitchmentEvent {
    public GlobalAporkalypse() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.addTimedEffect(player, MobEffects.HUNGER, durationTicks, 1);
        }
    }

    @Override
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.removeTimedEffect(player, MobEffects.HUNGER);
        }
    }
}
