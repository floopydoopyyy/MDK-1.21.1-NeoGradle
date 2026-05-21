package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RuinRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<RuinRitualTickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("loopypowers", "ruin_ritual_tick")
    );

    public static final StreamCodec<ByteBuf, RuinRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RuinRitualTickPayload::entityId,
            ByteBufCodecs.INT, RuinRitualTickPayload::ticks,
            RuinRitualTickPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}