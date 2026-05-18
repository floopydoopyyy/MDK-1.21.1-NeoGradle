package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NatureCageFxPayload(int cx, int cy, int cz, int radius, int thickness) implements CustomPacketPayload {
    public static final Type<NatureCageFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "nature_cage_fx"));

    public static final StreamCodec<FriendlyByteBuf, NatureCageFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.cx());
                buf.writeInt(payload.cy());
                buf.writeInt(payload.cz());
                buf.writeInt(payload.radius());
                buf.writeInt(payload.thickness());
            },
            buf -> new NatureCageFxPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}