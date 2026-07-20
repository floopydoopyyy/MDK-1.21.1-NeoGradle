package com.oliver.witchmod.events.globals;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Everyone online swaps places with someone else, all at once. */
public final class GlobalPlayerShuffle extends BewitchmentEvent {
    public GlobalPlayerShuffle() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        List<ServerPlayer> players = new ArrayList<>(level.players());
        if (players.size() < 2) {
            return;
        }
        List<Vec3> positions = players.stream().map(ServerPlayer::position).toList();
        // Rotate positions by one so every player moves to someone else's spot, no player keeps their own.
        for (int i = 0; i < players.size(); i++) {
            Vec3 pos = positions.get((i + 1) % positions.size());
            players.get(i).teleportTo(pos.x, pos.y, pos.z);
        }
    }
}
