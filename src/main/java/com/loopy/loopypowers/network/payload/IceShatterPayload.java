package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceShatterPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<IceShatterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_shatter"));
    public static final StreamCodec<FriendlyByteBuf, IceShatterPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); },
            buf -> new IceShatterPayload(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
    static { ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleIceShatter); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}