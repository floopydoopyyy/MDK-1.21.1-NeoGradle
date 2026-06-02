package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompelAuraPayload(int entityId) implements CustomPacketPayload {

    public static final Type<CompelAuraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "compel_aura"));

    public static final StreamCodec<FriendlyByteBuf, CompelAuraPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.entityId()),
                    buf -> new CompelAuraPayload(buf.readVarInt())
            );

    static {
        ClientPayloadRegistry.add(TYPE, CODEC, ClientPayloadHandler::handleCompelAura);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}