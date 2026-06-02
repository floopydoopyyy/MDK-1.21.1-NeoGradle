package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One-shot EXPLOSION_EMITTER burst at the player's position.
 * heavy=true  — entity collision: 4 particles, wide spread
 * heavy=false — block collision:  3 particles, tight spread
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

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {}));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}