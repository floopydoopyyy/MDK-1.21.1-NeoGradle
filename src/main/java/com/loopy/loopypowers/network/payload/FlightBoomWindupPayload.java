package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FlightBoomWindupPayload(int entityId, boolean isStart) implements CustomPacketPayload {
    public static final Type<FlightBoomWindupPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "flight_boom_windup"));

    public static final StreamCodec<FriendlyByteBuf, FlightBoomWindupPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeBoolean(payload.isStart());
            },
            buf -> new FlightBoomWindupPayload(buf.readInt(), buf.readBoolean())
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}