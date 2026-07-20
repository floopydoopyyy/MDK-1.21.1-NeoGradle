package com.oliver.witchmod.events.globals;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** One random online player takes a serious hit. Could've been anyone. */
public final class GlobalRussianRoulette extends BewitchmentEvent {
    private static final float DAMAGE_AMOUNT = 15.0F;

    public GlobalRussianRoulette() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer unlucky = players.get(level.random.nextInt(players.size()));
        unlucky.hurt(level.damageSources().magic(), DAMAGE_AMOUNT);
    }
}
