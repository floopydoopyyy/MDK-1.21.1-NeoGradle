package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlackoutFxPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<BlackoutFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "blackout_fx"));

    public static final StreamCodec<ByteBuf, BlackoutFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, BlackoutFxPayload::x,
            ByteBufCodecs.DOUBLE, BlackoutFxPayload::y,
            ByteBufCodecs.DOUBLE, BlackoutFxPayload::z,
            BlackoutFxPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}