package com.loopy.loopypowers.manager;

import com.loopy.loopypowers.ui.CooldownUI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class PassiveManager {

    // stores passive state per player
    private static final Map<UUID, Boolean> PASSIVES = new HashMap<>();

    public static boolean isEnabled(ServerPlayer player) {
        // default = true
        return PASSIVES.getOrDefault(player.getUUID(), true);
    }

    // Allows the PowerManager to load the state from the save file
    public static void setPassiveState(ServerPlayer player, boolean state) {
        PASSIVES.put(player.getUUID(), state);
    }

    public static void toggle(ServerPlayer player) {
        boolean newState = !isEnabled(player);
        PASSIVES.put(player.getUUID(), newState);

        sendFeedback(player, newState);

        // persist it
        PlayerDataStore.save(player);
    }

    private static void sendFeedback(ServerPlayer player, boolean enabled) {
        Component msg = enabled
                ? Component.translatable("message.loopypowers.passive_enabled").withStyle(ChatFormatting.GREEN)
                : Component.translatable("message.loopypowers.passive_disabled").withStyle(ChatFormatting.RED);

        CooldownUI.pushActionbarOverride(player, msg, 20);
    }
}