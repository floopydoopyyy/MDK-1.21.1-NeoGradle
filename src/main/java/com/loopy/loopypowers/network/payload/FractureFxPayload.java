package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FractureFxPayload(int ownerId, double x, double y, double z, long seed, boolean active) implements CustomPacketPayload {
    public static final Type<FractureFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "fracture_fx"));

    public static final StreamCodec<ByteBuf, FractureFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FractureFxPayload::ownerId,
            ByteBufCodecs.DOUBLE, FractureFxPayload::x,
            ByteBufCodecs.DOUBLE, FractureFxPayload::y,
            ByteBufCodecs.DOUBLE, FractureFxPayload::z,
            ByteBufCodecs.VAR_LONG, FractureFxPayload::seed, //  for some reason can't be long idk im a fresher
            ByteBufCodecs.BOOL, FractureFxPayload::active,
            FractureFxPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}