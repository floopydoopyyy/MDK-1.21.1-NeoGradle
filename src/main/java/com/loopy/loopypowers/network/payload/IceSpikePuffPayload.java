package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent once per spike tip when it finishes rising to full height.
 */
public record IceSpikePuffPayload(double x, double y, double z)
        implements CustomPacketPayload {

    public static final Type<IceSpikePuffPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_spike_puff"));

    public static final StreamCodec<FriendlyByteBuf, IceSpikePuffPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); },
            buf -> new IceSpikePuffPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
