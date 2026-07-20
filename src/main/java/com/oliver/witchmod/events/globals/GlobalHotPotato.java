package com.oliver.witchmod.events.globals;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Somebody ends up holding it too long. */
public final class GlobalHotPotato extends BewitchmentEvent {
    private static final float BURN_SECONDS = 3.0F;

    public GlobalHotPotato() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        players.get(level.random.nextInt(players.size())).igniteForSeconds(BURN_SECONDS);
    }
}
