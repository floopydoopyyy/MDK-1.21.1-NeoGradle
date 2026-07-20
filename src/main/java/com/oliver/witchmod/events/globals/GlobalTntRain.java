package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.PrimedTnt;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** It's raining TNT. Everyone online gets a few falling their way. */
public final class GlobalTntRain extends BewitchmentEvent {
    private static final int TNT_PER_PLAYER = 2;
    private static final int HEIGHT_ABOVE = 15;

    public GlobalTntRain() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < TNT_PER_PLAYER; i++) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 6.0;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 6.0;
                PrimedTnt tnt = new PrimedTnt(level, x, player.getY() + HEIGHT_ABOVE, z, null);
                level.addFreshEntity(tnt);
            }
        }
    }
}
