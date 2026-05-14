package com.loopy.loopypowers.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RenderPackets {
    private RenderPackets() {}

    public static void hidePlayerFromOthers(ServerPlayer hidden, int ticks) {
        // NeoForge natively handles sending to tracking players and the player themselves!
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(hidden,
                new AbilityPackets.HidePlayerPayload(hidden.getId(), ticks)
        );
    }

    // client only trail
    public static void sendResonanceTrail(ServerPlayer viewer, int targetEntityId, int count, float intensity) {
        PacketDistributor.sendToPlayer(viewer,
                new AbilityPackets.ResonanceTrailPayload(targetEntityId, count, intensity)
        );
    }

    // small ring at a target. the things I do for a power no one will care about.
    public static void sendResonanceRing(ServerPlayer viewer, int targetEntityId, float intensity) {
        PacketDistributor.sendToPlayer(viewer,
                new AbilityPackets.ResonanceRingPayload(targetEntityId, intensity)
        );
    }

    // 1s line between viewer and target
    public static void sendResonanceLine(ServerPlayer viewer, int targetEntityId, int ticks, float intensity) {
        PacketDistributor.sendToPlayer(viewer,
                new AbilityPackets.ResonanceLinePayload(targetEntityId, ticks, intensity)
        );
    }

    // stun fx
    public static void sendStunAudio(ServerPlayer viewer, int ticks) {
        PacketDistributor.sendToPlayer(viewer,
                new AbilityPackets.StunAudioPayload(ticks)
        );
    }
}