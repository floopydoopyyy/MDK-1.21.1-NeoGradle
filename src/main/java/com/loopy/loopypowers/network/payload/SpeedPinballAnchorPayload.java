package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every 3 server ticks while pinball is active (the gate lives
 * server-side, so the client simply renders unconditionally on receipt).
 * Carries the fixed anchor position used to draw the boundary ring.
 */
public record SpeedPinballAnchorPayload(
        double anchorX, double anchorY, double anchorZ
) implements CustomPacketPayload {

    public static final Type<SpeedPinballAnchorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_pinball_anchor"));

    public static final StreamCodec<FriendlyByteBuf, SpeedPinballAnchorPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.anchorX);
                        buf.writeDouble(p.anchorY);
                        buf.writeDouble(p.anchorZ);
                    },
                    buf -> new SpeedPinballAnchorPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble()
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
