package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent once when CompelEntity hits a target (server → all tracking clients).
 * Unlike PuppetryHitPayload, CompelEntity has no FX ring at the projectile
 * position, so only the target entity ID and life angle-seed are needed.
 */
public record CompelHitPayload(
        int targetEntityId,
        int life
) implements CustomPacketPayload {

    public static final Type<CompelHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "compel_hit"));

    public static final StreamCodec<FriendlyByteBuf, CompelHitPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.targetEntityId);
                        buf.writeVarInt(p.life);
                    },
                    buf -> new CompelHitPayload(
                            buf.readVarInt(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}