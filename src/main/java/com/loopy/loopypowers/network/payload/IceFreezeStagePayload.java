package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick for each frozen entity while they have freeze points.
 *
 * @param entityId  The frozen entity's ID.
 * @param stage     Current freeze stage (1–5).
 * @param gameTime  Server game time — used by the client to throttle
 *                  lower stages and drive the shatter-ring rotation angle.
 */
public record IceFreezeStagePayload(int entityId, int stage, long gameTime)
        implements CustomPacketPayload {

    public static final Type<IceFreezeStagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_freeze_stage"));

    public static final StreamCodec<FriendlyByteBuf, IceFreezeStagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeByte(p.stage()); buf.writeLong(p.gameTime()); },
            buf -> new IceFreezeStagePayload(buf.readInt(), buf.readByte(), buf.readLong())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
