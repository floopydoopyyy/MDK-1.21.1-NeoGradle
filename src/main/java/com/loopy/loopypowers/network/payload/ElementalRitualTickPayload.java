package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ElementalRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<ElementalRitualTickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("loopypowers", "elemental_ritual_tick")
    );

    public static final StreamCodec<ByteBuf, ElementalRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ElementalRitualTickPayload::entityId,
            ByteBufCodecs.INT, ElementalRitualTickPayload::ticks,
            ElementalRitualTickPayload::new
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}