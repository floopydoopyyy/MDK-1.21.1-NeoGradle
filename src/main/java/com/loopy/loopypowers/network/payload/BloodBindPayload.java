package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BloodBindPayload(int casterId, int targetId, boolean active) implements CustomPacketPayload {
    public static final Type<BloodBindPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "blood_bind"));

    public static final StreamCodec<ByteBuf, BloodBindPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,  BloodBindPayload::casterId,
            ByteBufCodecs.INT,  BloodBindPayload::targetId,
            ByteBufCodecs.BOOL, BloodBindPayload::active,
            BloodBindPayload::new
    );

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleBloodBind);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}