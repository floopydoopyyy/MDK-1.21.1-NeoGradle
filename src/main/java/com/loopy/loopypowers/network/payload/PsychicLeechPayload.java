package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PsychicLeechPayload(int targetId, int casterId) implements CustomPacketPayload {

    public static final Type<PsychicLeechPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "psychic_leech"));

    public static final StreamCodec<FriendlyByteBuf, PsychicLeechPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.targetId());
                        buf.writeVarInt(p.casterId());
                    },
                    buf -> new PsychicLeechPayload(buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}