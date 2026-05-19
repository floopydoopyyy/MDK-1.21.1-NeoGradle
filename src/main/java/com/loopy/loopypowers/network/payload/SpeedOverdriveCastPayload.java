package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent once when the player activates Overdrive.
 * Only position is needed — the cast FX is a radial burst centred on the player.
 */
public record SpeedOverdriveCastPayload(
        double x, double y, double z
) implements CustomPacketPayload {

    public static final Type<SpeedOverdriveCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_overdrive_cast"));

    public static final StreamCodec<FriendlyByteBuf, SpeedOverdriveCastPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); },
                    buf -> new SpeedOverdriveCastPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
