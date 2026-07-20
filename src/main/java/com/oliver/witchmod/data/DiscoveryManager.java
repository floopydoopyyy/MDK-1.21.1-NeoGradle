package com.oliver.witchmod.data;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tracks which curses/blessings and neutrals/globals each player has discovered (CLAUDE.md section 2.7):
 * "the victim discovers on trigger; the caster discovers the instant they successfully send it."
 *
 * <p>Effects are covered by a single hook in {@link EffectManager#apply} — every curse/blessing
 * application, regardless of source (command, item, Table), passes through there, so this doesn't need
 * to be bolted onto each of the 72 individual {@code Effect} subclasses. Events (neutrals/globals) are
 * covered at their two trigger call sites ({@code BewitchCommand} and {@code BewitchingTableRitual}).
 */
public final class DiscoveryManager {
    private DiscoveryManager() {}

    public static void markEffectDiscovered(ServerPlayer player, ResourceLocation effectId) {
        Set<ResourceLocation> discovered = player.getData(WitchModAttachments.DISCOVERED_EFFECTS);
        discovered.add(effectId);
        player.setData(WitchModAttachments.DISCOVERED_EFFECTS, discovered);
    }

    public static boolean hasDiscoveredEffect(ServerPlayer player, ResourceLocation effectId) {
        return player.getExistingData(WitchModAttachments.DISCOVERED_EFFECTS)
                .map(set -> set.contains(effectId))
                .orElse(false);
    }

    public static void markEventDiscovered(ServerPlayer player, ResourceLocation eventId) {
        Set<ResourceLocation> discovered = player.getData(WitchModAttachments.DISCOVERED_EVENTS);
        discovered.add(eventId);
        player.setData(WitchModAttachments.DISCOVERED_EVENTS, discovered);
    }

    public static boolean hasDiscoveredEvent(ServerPlayer player, ResourceLocation eventId) {
        return player.getExistingData(WitchModAttachments.DISCOVERED_EVENTS)
                .map(set -> set.contains(eventId))
                .orElse(false);
    }
}
