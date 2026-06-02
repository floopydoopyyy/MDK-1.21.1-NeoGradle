package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LightningSuperchargeBurstPayload(int entityId) implements CustomPacketPayload {
    public static final Type<LightningSuperchargeBurstPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "lightning_supercharge_burst"));
    public static final StreamCodec<FriendlyByteBuf, LightningSuperchargeBurstPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.entityId()),
            buf -> new LightningSuperchargeBurstPayload(buf.readVarInt()));
    static { ClientPayloadRegistry.add(TYPE, CODEC, ClientPayloadHandler::handleLightningSuperchargeBurst); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}