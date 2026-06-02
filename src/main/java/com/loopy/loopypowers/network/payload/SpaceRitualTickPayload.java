package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpaceRitualTickPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<SpaceRitualTickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("loopypowers", "space_ritual_tick")
    );

    public static final StreamCodec<ByteBuf, SpaceRitualTickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SpaceRitualTickPayload::entityId,
            ByteBufCodecs.INT, SpaceRitualTickPayload::ticks,
            SpaceRitualTickPayload::new
    );

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleSpaceRitual);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}