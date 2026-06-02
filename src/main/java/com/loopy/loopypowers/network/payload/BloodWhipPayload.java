package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BloodWhipPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<BloodWhipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "blood_whip"));

    public static final StreamCodec<ByteBuf, BloodWhipPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,    BloodWhipPayload::entityId,
            ByteBufCodecs.DOUBLE, BloodWhipPayload::x,
            ByteBufCodecs.DOUBLE, BloodWhipPayload::y,
            ByteBufCodecs.DOUBLE, BloodWhipPayload::z,
            BloodWhipPayload::new
    );

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleBloodWhip);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}