package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Everyone gets scattered to a random spot nearby. */
public final class GlobalSpotShuffle extends BewitchmentEvent {
    private static final double SCATTER_RADIUS = 32.0;

    public GlobalSpotShuffle() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            double dx = (level.random.nextDouble() - 0.5) * 2 * SCATTER_RADIUS;
            double dz = (level.random.nextDouble() - 0.5) * 2 * SCATTER_RADIUS;
            player.teleportTo(player.getX() + dx, player.getY(), player.getZ() + dz);
        }
    }
}
