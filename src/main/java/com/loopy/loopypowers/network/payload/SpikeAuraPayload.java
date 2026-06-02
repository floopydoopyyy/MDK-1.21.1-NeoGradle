package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick to render the Spike (stun/root) aura particles on a target.
 */
public record SpikeAuraPayload(int entityId) implements CustomPacketPayload {

    public static final Type<SpikeAuraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "spike_aura"));

    public static final StreamCodec<FriendlyByteBuf, SpikeAuraPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.entityId()),
                    buf -> new SpikeAuraPayload(buf.readVarInt())
            );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}