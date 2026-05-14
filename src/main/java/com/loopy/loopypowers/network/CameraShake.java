package com.loopy.loopypowers.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CameraShake {
    private CameraShake() {}

    /**
     * Shake nearby players' cameras.
     * @param source The player causing the shake (center point)
     * @param radius Blocks radius around source
     * @param ticks Duration in ticks (client-side)
     * @param strength Small number e.g. 0.4f - 1.2f (keep mild)
     */
    public static void shakeNearby(ServerPlayer source, double radius, int ticks, float strength) {
        // getServerWorld becomes serverLevel, getPos becomes position
        var world = source.serverLevel();
        var origin = source.position();
        double r2 = radius * radius;

        for (ServerPlayer p : world.players()) {
            // squaredDistanceTo becomes distanceToSqr
            if (p.distanceToSqr(origin) <= r2) {
                // Fabric's ServerPlayNetworking becomes NeoForge's PacketDistributor
                PacketDistributor.sendToPlayer(p, new AbilityPackets.CameraShakePayload(ticks, strength));
            }
        }
    }

    // shake one player
    public static void shake(ServerPlayer target, int ticks, float strength) {
        PacketDistributor.sendToPlayer(target, new AbilityPackets.CameraShakePayload(ticks, strength));
    }
    // this is so much cleaner
}