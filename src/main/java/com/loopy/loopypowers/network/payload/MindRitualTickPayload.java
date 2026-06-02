package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MindRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<MindRitualTickPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "mind_ritual_tick"));
    public static final StreamCodec<ByteBuf, MindRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, MindRitualTickPayload::entityId,
            ByteBufCodecs.INT, MindRitualTickPayload::ticks,
            MindRitualTickPayload::new);
    static { ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleMindRitual); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}