package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent once when PuppetryEntity hits a target (server → all tracking clients).
 * The client looks up the target entity by ID to get its current position/height,
 * and also renders a ring burst at the projectile's last known position.
 */
public record PuppetryHitPayload(
        int targetEntityId,
        double projX, double projY, double projZ,
        int life
) implements CustomPacketPayload {

    public static final Type<PuppetryHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "puppetry_hit"));

    public static final StreamCodec<FriendlyByteBuf, PuppetryHitPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.targetEntityId);
                        buf.writeDouble(p.projX);
                        buf.writeDouble(p.projY);
                        buf.writeDouble(p.projZ);
                        buf.writeVarInt(p.life);
                    },
                    buf -> new PuppetryHitPayload(
                            buf.readVarInt(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}