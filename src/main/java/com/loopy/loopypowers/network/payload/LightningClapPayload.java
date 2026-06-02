package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LightningClapPayload(int entityId, boolean isParty) implements CustomPacketPayload {
    public static final Type<LightningClapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "lightning_clap"));
    public static final StreamCodec<FriendlyByteBuf, LightningClapPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.entityId()); buf.writeBoolean(p.isParty()); },
            buf -> new LightningClapPayload(buf.readVarInt(), buf.readBoolean()));
    static { ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {})); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}