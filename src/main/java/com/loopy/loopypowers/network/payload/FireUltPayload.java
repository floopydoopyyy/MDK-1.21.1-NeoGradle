package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FireUltPayload(int ownerId, boolean active) implements CustomPacketPayload {
    public static final Type<FireUltPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "fire_ult_fx"));

    public static final StreamCodec<ByteBuf, FireUltPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FireUltPayload::ownerId,
            ByteBufCodecs.BOOL, FireUltPayload::active,
            FireUltPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}