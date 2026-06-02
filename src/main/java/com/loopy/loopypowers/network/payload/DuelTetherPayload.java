package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DuelTetherPayload(int entityAId, int entityBId) implements CustomPacketPayload {
    public static final Type<DuelTetherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "duel_tether"));

    public static final StreamCodec<FriendlyByteBuf, DuelTetherPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityAId());
                buf.writeInt(payload.entityBId());
            },
            buf -> new DuelTetherPayload(buf.readInt(), buf.readInt())
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}