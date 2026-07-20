package com.oliver.witchmod.events.globals;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Someone online wins the lot. */
public final class GlobalAuction extends BewitchmentEvent {
    public GlobalAuction() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer winner = players.get(level.random.nextInt(players.size()));
        ItemStack prize = new ItemStack(Items.DIAMOND, 3);
        if (!winner.getInventory().add(prize)) {
            winner.drop(prize, false);
        }
    }
}
