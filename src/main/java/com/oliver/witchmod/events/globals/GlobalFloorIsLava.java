package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** The global version of the curse — everyone standing on solid ground right now feels it. */
public final class GlobalFloorIsLava extends BewitchmentEvent {
    private static final float BURN_SECONDS = 2.0F;

    public GlobalFloorIsLava() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            if (player.onGround()) {
                player.igniteForSeconds(BURN_SECONDS);
            }
        }
    }
}
