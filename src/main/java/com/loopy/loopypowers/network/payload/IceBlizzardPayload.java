package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick while the Blizzard ultimate is active.
 * The client uses the caster's live position plus ultTick as a random
 * seed offset to generate the per-tick overhead snowflakes and gusts,
 * and scans its own nearby entity list for the per-entity snow swirls.
 *
 * @param entityId  Caster's entity ID.
 * @param ultTick   Remaining ult ticks (used as random seed offset).
 */
public record IceBlizzardPayload(int entityId, int ultTick)
        implements CustomPacketPayload {

    public static final Type<IceBlizzardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_blizzard"));

    public static final StreamCodec<FriendlyByteBuf, IceBlizzardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeInt(p.ultTick()); },
            buf -> new IceBlizzardPayload(buf.readInt(), buf.readInt())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
