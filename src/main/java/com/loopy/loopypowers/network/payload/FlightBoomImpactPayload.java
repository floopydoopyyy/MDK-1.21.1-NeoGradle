package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record FlightBoomImpactPayload(Vec3 pos) implements CustomPacketPayload {
    public static final Type<FlightBoomImpactPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "flight_boom_impact"));

    public static final StreamCodec<FriendlyByteBuf, FlightBoomImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.pos().x);
                buf.writeDouble(payload.pos().y);
                buf.writeDouble(payload.pos().z);
            },
            buf -> new FlightBoomImpactPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}