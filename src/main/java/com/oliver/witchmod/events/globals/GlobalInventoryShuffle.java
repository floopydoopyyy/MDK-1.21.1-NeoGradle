package com.oliver.witchmod.events.globals;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Every online player's inventory gets swapped with someone else's. Server-wide chaos. */
public final class GlobalInventoryShuffle extends BewitchmentEvent {
    public GlobalInventoryShuffle() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        List<ServerPlayer> players = new ArrayList<>(level.players());
        if (players.size() < 2) {
            return;
        }
        for (int i = players.size() - 1; i > 0; i--) {
            int j = level.random.nextInt(i + 1);
            ServerPlayer temp = players.get(i);
            players.set(i, players.get(j));
            players.set(j, temp);
        }
        for (int i = 0; i + 1 < players.size(); i += 2) {
            NonNullList<ItemStack> a = players.get(i).getInventory().items;
            NonNullList<ItemStack> b = players.get(i + 1).getInventory().items;
            for (int slot = 0; slot < a.size(); slot++) {
                ItemStack temp = a.get(slot);
                a.set(slot, b.get(slot));
                b.set(slot, temp);
            }
        }
    }
}
