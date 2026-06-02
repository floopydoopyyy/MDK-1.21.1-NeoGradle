package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick to render the Possessed (ultimate) aura particles on a target.
 */
public record PossessedAuraPayload(int entityId) implements CustomPacketPayload {

    public static final Type<PossessedAuraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "possessed_aura"));

    public static final StreamCodec<FriendlyByteBuf, PossessedAuraPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.entityId()),
                    buf -> new PossessedAuraPayload(buf.readVarInt())
            );

    static {
        ClientPayloadRegistry.add(TYPE, CODEC, ClientPayloadHandler::handlePossessedAura);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}