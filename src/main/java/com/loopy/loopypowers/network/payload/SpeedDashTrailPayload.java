package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick while the dash (or pinball travel) is active.
 * Velocity is needed to derive the "back" direction for the streak trail.
 * gameTime is forwarded so the client can replicate the every-other-tick
 * SWEEP_ATTACK guard exactly.
 */
public record SpeedDashTrailPayload(
        double x, double y, double z,
        double vx, double vy, double vz,
        long gameTime
) implements CustomPacketPayload {

    public static final Type<SpeedDashTrailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_dash_trail"));

    public static final StreamCodec<FriendlyByteBuf, SpeedDashTrailPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.x);  buf.writeDouble(p.y);  buf.writeDouble(p.z);
                        buf.writeDouble(p.vx); buf.writeDouble(p.vy); buf.writeDouble(p.vz);
                        buf.writeLong(p.gameTime);
                    },
                    buf -> new SpeedDashTrailPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readLong()
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
