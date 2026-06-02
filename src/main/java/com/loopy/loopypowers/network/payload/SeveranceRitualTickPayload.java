package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SeveranceRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<SeveranceRitualTickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("loopypowers", "severance_ritual_tick")
    );

    public static final StreamCodec<ByteBuf, SeveranceRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SeveranceRitualTickPayload::entityId,
            ByteBufCodecs.INT, SeveranceRitualTickPayload::ticks,
            SeveranceRitualTickPayload::new
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}