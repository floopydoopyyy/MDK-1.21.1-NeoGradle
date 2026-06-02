package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PowerRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<PowerRitualTickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("loopypowers", "power_ritual_tick")
    );

    public static final StreamCodec<ByteBuf, PowerRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PowerRitualTickPayload::entityId,
            ByteBufCodecs.INT, PowerRitualTickPayload::ticks,
            PowerRitualTickPayload::new
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}