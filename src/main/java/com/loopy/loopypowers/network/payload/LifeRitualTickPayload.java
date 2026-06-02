package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LifeRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<LifeRitualTickPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "life_ritual_tick"));
    public static final StreamCodec<ByteBuf, LifeRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LifeRitualTickPayload::entityId,
            ByteBufCodecs.INT, LifeRitualTickPayload::ticks,
            LifeRitualTickPayload::new);
    static { ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleLifeRitual); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}