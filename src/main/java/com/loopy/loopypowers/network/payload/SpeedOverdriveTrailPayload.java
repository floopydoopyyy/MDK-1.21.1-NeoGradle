package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick while Overdrive is active.
 * Velocity drives the "back" and "right" direction math for the layered
 * trail passes. gameTime is forwarded to replicate the every-4-ticks
 * side-ring guard on the client.
 */
public record SpeedOverdriveTrailPayload(
        double x, double y, double z,
        double vx, double vy, double vz,
        long gameTime
) implements CustomPacketPayload {

    public static final Type<SpeedOverdriveTrailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_overdrive_trail"));

    public static final StreamCodec<FriendlyByteBuf, SpeedOverdriveTrailPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.x);  buf.writeDouble(p.y);  buf.writeDouble(p.z);
                        buf.writeDouble(p.vx); buf.writeDouble(p.vy); buf.writeDouble(p.vz);
                        buf.writeLong(p.gameTime);
                    },
                    buf -> new SpeedOverdriveTrailPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readLong()
                    )
            );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {}));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}