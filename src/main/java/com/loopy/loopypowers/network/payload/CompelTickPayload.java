package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick by CompelEntity (server → all tracking clients).
 * Carries the position, velocity direction, and life counter needed
 * to reconstruct the single-helix + core-line particle trail on the client.
 */
public record CompelTickPayload(
        double x, double y, double z,
        double vx, double vy, double vz,
        int life
) implements CustomPacketPayload {

    public static final Type<CompelTickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "compel_tick"));

    public static final StreamCodec<FriendlyByteBuf, CompelTickPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.x);
                        buf.writeDouble(p.y);
                        buf.writeDouble(p.z);
                        buf.writeDouble(p.vx);
                        buf.writeDouble(p.vy);
                        buf.writeDouble(p.vz);
                        buf.writeVarInt(p.life);
                    },
                    buf -> new CompelTickPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}