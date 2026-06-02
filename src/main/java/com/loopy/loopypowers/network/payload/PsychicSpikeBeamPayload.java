package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record PsychicSpikeBeamPayload(Vec3 start, Vec3 end, boolean isChain) implements CustomPacketPayload {

    public static final Type<PsychicSpikeBeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "psychic_spike_beam"));

    public static final StreamCodec<FriendlyByteBuf, PsychicSpikeBeamPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.start().x); buf.writeDouble(p.start().y); buf.writeDouble(p.start().z);
                        buf.writeDouble(p.end().x);   buf.writeDouble(p.end().y);   buf.writeDouble(p.end().z);
                        buf.writeBoolean(p.isChain());
                    },
                    buf -> new PsychicSpikeBeamPayload(
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readBoolean()
                    )
            );

    static {
        ClientPayloadRegistry.add(TYPE, CODEC, ClientPayloadHandler::handleSpikeBeam);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}