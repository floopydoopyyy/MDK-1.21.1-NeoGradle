package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompelHitPayload(int targetEntityId, int life) implements CustomPacketPayload {

    public static final Type<CompelHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "compel_hit"));

    public static final StreamCodec<FriendlyByteBuf, CompelHitPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.targetEntityId);
                        buf.writeVarInt(p.life);
                    },
                    buf -> new CompelHitPayload(buf.readVarInt(), buf.readVarInt())
            );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}