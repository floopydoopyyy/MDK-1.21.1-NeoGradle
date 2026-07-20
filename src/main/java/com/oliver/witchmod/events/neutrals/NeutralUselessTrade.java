package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** You traded something for this. You're still not sure what, or why. */
public final class NeutralUselessTrade extends BewitchmentEvent {
    public NeutralUselessTrade() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        ItemStack junk = new ItemStack(Items.STICK, 1);
        if (!initiator.getInventory().add(junk)) {
            initiator.drop(junk, false);
        }
        initiator.displayClientMessage(Component.literal("You traded for... a stick. Great."), true);
    }
}
