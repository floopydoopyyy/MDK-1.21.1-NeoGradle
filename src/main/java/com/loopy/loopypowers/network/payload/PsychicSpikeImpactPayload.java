package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PsychicSpikeImpactPayload(int entityId) implements CustomPacketPayload {

    public static final Type<PsychicSpikeImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "psychic_spike_impact"));

    public static final StreamCodec<FriendlyByteBuf, PsychicSpikeImpactPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.entityId()),
                    buf -> new PsychicSpikeImpactPayload(buf.readVarInt())
            );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, CODEC, (payload, ctx) -> {}));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}