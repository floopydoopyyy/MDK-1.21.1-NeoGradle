package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceFreezeStagePayload(int entityId, int stage, long gameTime) implements CustomPacketPayload {
    public static final Type<IceFreezeStagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_freeze_stage"));
    public static final StreamCodec<FriendlyByteBuf, IceFreezeStagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeByte(p.stage()); buf.writeLong(p.gameTime()); },
            buf -> new IceFreezeStagePayload(buf.readInt(), buf.readByte(), buf.readLong()));
    static { ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {})); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}