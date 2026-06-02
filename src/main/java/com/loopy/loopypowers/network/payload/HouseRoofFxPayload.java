package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HouseRoofFxPayload(int cx, int cz, double y, int radius) implements CustomPacketPayload {
    public static final Type<HouseRoofFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "house_roof_fx"));

    public static final StreamCodec<FriendlyByteBuf, HouseRoofFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.cx());
                buf.writeInt(payload.cz());
                buf.writeDouble(payload.y());
                buf.writeInt(payload.radius());
            },
            buf -> new HouseRoofFxPayload(buf.readInt(), buf.readInt(), buf.readDouble(), buf.readInt())
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}