package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One-shot EXPLOSION_EMITTER burst at the player's position.
 * Used by two overdrive collision events that share the same particle type
 * but differ in count and spread:
 *
 *   heavy=true  — entity collision: 4 particles, wide spread  (0.6/0.2/0.6, speed 0.10)
 *   heavy=false — block collision:  3 particles, tight spread (0.15/0.10/0.15, speed 0.02)
 */
public record SpeedExplosionFxPayload(
        double x, double y, double z,
        boolean heavy
) implements CustomPacketPayload {

    public static final Type<SpeedExplosionFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_explosion_fx"));

    public static final StreamCodec<FriendlyByteBuf, SpeedExplosionFxPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
                        buf.writeBoolean(p.heavy);
                    },
                    buf -> new SpeedExplosionFxPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readBoolean()
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
