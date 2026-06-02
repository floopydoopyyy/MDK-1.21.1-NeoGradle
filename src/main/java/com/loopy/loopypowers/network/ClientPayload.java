package com.loopy.loopypowers.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Marker interface for all client-bound (server → client) payload types.
 * Each payload registers itself into ClientPayloadRegistry via a static block,
 * so AbilityPackets.register() picks them all up with a single call.
 */
public interface ClientPayload<T extends CustomPacketPayload> {
    void registerWith(PayloadRegistrar registrar);
}