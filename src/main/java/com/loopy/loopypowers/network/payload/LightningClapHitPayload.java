package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LightningClapHitPayload(int targetId, float t, boolean isParty) implements CustomPacketPayload {

    public static final Type<LightningClapHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "lightning_clap_hit"));

    public static final StreamCodec<FriendlyByteBuf, LightningClapHitPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.targetId());
                        buf.writeFloat(p.t());
                        buf.writeBoolean(p.isParty());
                    },
                    buf -> new LightningClapHitPayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}