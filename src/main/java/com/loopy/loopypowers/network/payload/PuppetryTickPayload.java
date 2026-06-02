package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick by PuppetryEntity (server → all tracking clients).
 * Carries the position, velocity direction, and life counter needed
 * to reconstruct the double-helix + core-line particle trail on the client.
 */
public record PuppetryTickPayload(
        double x, double y, double z,
        double vx, double vy, double vz,
        int life
) implements CustomPacketPayload {

    public static final Type<PuppetryTickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "puppetry_tick"));

    public static final StreamCodec<FriendlyByteBuf, PuppetryTickPayload> CODEC =
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
                    buf -> new PuppetryTickPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readVarInt()
                    )
            );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}