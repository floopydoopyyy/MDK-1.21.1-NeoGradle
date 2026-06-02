package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent once when the passive low-health speed burst triggers.
 */
public record SpeedLowHealthBurstPayload(
        double x, double y, double z
) implements CustomPacketPayload {

    public static final Type<SpeedLowHealthBurstPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_low_health_burst"));

    public static final StreamCodec<FriendlyByteBuf, SpeedLowHealthBurstPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); },
                    buf -> new SpeedLowHealthBurstPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    static {
        ClientPayloadRegistry.add(TYPE, CODEC, ClientPayloadHandler::handleSpeedLowHealthBurst);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}