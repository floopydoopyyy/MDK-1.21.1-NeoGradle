package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExplosionDropZonePayload(int ownerId, boolean active, int stage) implements CustomPacketPayload {
    public static final Type<ExplosionDropZonePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "explosion_drop_zone"));

    public static final StreamCodec<ByteBuf, ExplosionDropZonePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,  ExplosionDropZonePayload::ownerId,
            ByteBufCodecs.BOOL, ExplosionDropZonePayload::active,
            ByteBufCodecs.INT,  ExplosionDropZonePayload::stage,
            ExplosionDropZonePayload::new
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}