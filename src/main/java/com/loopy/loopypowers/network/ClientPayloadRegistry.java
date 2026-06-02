package com.loopy.loopypowers.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores client-bound payload registrations until RegisterPayloadHandlersEvent fires.
 *
 * Each payload's static block calls add() with its type, codec, and a client handler.
 * On the server the handler lambda is never invoked (server never receives playToClient
 * packets), so referencing client classes inside it is safe as long as they are inside
 * a lambda (not at class-load time).
 *
 * registerAll() is called once from AbilityPackets.register() and registers every
 * channel exactly once with playToClient.
 */
public final class ClientPayloadRegistry {

    private ClientPayloadRegistry() {}

    private record Entry<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<?, T> codec,
            IPayloadHandler<T> handler
    ) {
        @SuppressWarnings("unchecked")
        void register(PayloadRegistrar registrar) {
            registrar.playToClient(type, (StreamCodec) codec, handler);
        }
    }

    private static final List<Entry<?>> ALL = new ArrayList<>();

    public static <T extends CustomPacketPayload> void add(
            CustomPacketPayload.Type<T> type,
            StreamCodec<?, T> codec,
            IPayloadHandler<T> handler
    ) {
        ALL.add(new Entry<>(type, codec, handler));
    }

    public static void registerAll(PayloadRegistrar registrar) {
        for (Entry<?> e : ALL) {
            e.register(registrar);
        }
    }
}