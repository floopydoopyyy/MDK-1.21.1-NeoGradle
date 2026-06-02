package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceBlizzardPayload(int entityId, int ultTick) implements CustomPacketPayload {
    public static final Type<IceBlizzardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_blizzard"));
    public static final StreamCodec<FriendlyByteBuf, IceBlizzardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeInt(p.ultTick()); },
            buf -> new IceBlizzardPayload(buf.readInt(), buf.readInt()));
    static { ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {})); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}