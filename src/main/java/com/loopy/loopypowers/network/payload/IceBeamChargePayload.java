package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick while the beam is charging.
 * The client reads the player's eye position, look vector, and tickCount
 * to reproduce the 14-point rotating charge ring at the muzzle.
 *
 * @param entityId  Caster's entity ID.
 * @param tickCount Player's tickCount — drives the ring rotation angle.
 */
public record IceBeamChargePayload(int entityId, int tickCount)
        implements CustomPacketPayload {

    public static final Type<IceBeamChargePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_beam_charge"));

    public static final StreamCodec<FriendlyByteBuf, IceBeamChargePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeInt(p.tickCount()); },
            buf -> new IceBeamChargePayload(buf.readInt(), buf.readInt())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
