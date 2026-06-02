package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceBeamFirePayload(
        double startX, double startY, double startZ,
        double endX,   double endY,   double endZ,
        long gameTime
) implements CustomPacketPayload {

    public static final Type<IceBeamFirePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_beam_fire"));

    public static final StreamCodec<FriendlyByteBuf, IceBeamFirePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.startX()); buf.writeDouble(p.startY()); buf.writeDouble(p.startZ());
                buf.writeDouble(p.endX());   buf.writeDouble(p.endY());   buf.writeDouble(p.endZ());
                buf.writeLong(p.gameTime());
            },
            buf -> new IceBeamFirePayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readLong()
            )
    );

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleIceBeamFire);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}