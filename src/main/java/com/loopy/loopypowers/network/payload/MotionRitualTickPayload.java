package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MotionRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<MotionRitualTickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("loopypowers", "motion_ritual_tick")
    );

    public static final StreamCodec<ByteBuf, MotionRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, MotionRitualTickPayload::entityId,
            ByteBufCodecs.INT, MotionRitualTickPayload::ticks,
            MotionRitualTickPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}