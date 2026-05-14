package com.loopy.loopypowers.manager;

import com.loopy.loopypowers.ui.CooldownUI;
import com.loopy.loopypowers.power.FlightPower;
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
        return PASSIVES.getOrDefault(player.getUUID(), true); // getUuid -> getUUID
    }

    public static void toggle(ServerPlayer player) {
        // block for flight power
        if (PowerManager.getPower(player) instanceof FlightPower) {
            sendBlockedFeedback(player);
            return;
        }

        boolean newState = !isEnabled(player);
        PASSIVES.put(player.getUUID(), newState);

        sendFeedback(player, newState);
    }

    private static void sendFeedback(ServerPlayer player, boolean enabled) {
        // Text -> Component, Formatting -> ChatFormatting
        Component msg = enabled
                ? Component.translatable("message.loopypowers.passive_enabled").withStyle(ChatFormatting.GREEN)
                : Component.translatable("message.loopypowers.passive_disabled").withStyle(ChatFormatting.RED);

        CooldownUI.pushActionbarOverride(player, msg, 20);
    }

    private static void sendBlockedFeedback(ServerPlayer player) {
        Component msg = Component.translatable("message.loopypowers.passive_blocked").withStyle(ChatFormatting.RED);
        CooldownUI.pushActionbarOverride(player, msg, 20);
    }
}