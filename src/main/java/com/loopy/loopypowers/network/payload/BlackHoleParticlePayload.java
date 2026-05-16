package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlackHoleParticlePayload(int entityId, float lifeProgress) implements CustomPacketPayload {

    // id
    public static final Type<BlackHoleParticlePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "bh_particles"));

    // tell network stuff
    public static final StreamCodec<ByteBuf, BlackHoleParticlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, BlackHoleParticlePayload::entityId,
            ByteBufCodecs.FLOAT, BlackHoleParticlePayload::lifeProgress,
            BlackHoleParticlePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}