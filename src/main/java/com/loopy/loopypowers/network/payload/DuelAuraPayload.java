package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DuelAuraPayload(int entityId) implements CustomPacketPayload {
    public static final Type<DuelAuraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "duel_aura"));

    public static final StreamCodec<FriendlyByteBuf, DuelAuraPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.entityId()),
            buf -> new DuelAuraPayload(buf.readInt())
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}