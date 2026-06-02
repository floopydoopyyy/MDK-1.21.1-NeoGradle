package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceSpikeTrailPayload(
        double startX, double startZ,
        double targetX, double targetZ,
        float progress, int seed, int yHint
) implements CustomPacketPayload {
    public static final Type<IceSpikeTrailPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_spike_trail"));
    public static final StreamCodec<FriendlyByteBuf, IceSpikeTrailPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.startX()); buf.writeDouble(p.startZ());
                buf.writeDouble(p.targetX()); buf.writeDouble(p.targetZ());
                buf.writeFloat(p.progress());
                buf.writeInt(p.seed()); buf.writeInt(p.yHint());
            },
            buf -> new IceSpikeTrailPayload(
                    buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(),
                    buf.readFloat(),
                    buf.readInt(), buf.readInt()));
    static { ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {})); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}