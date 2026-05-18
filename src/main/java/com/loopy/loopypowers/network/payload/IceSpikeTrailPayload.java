package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every tick while a spike cast is travelling toward its target.
 * The client recomputes the 11-step trail window and performs its own
 * lightweight surface-Y lookup using the same algorithm as the server.
 *
 * @param startX   Caster XZ origin (X component).
 * @param startZ   Caster XZ origin (Z component).
 * @param targetX  Target XZ (X component).
 * @param targetZ  Target XZ (Z component).
 * @param progress Interpolation progress [0..1].
 * @param seed     Deterministic jitter seed — same value used server-side.
 * @param yHint    Surface Y hint passed to the terrain search.
 */
public record IceSpikeTrailPayload(
        double startX, double startZ,
        double targetX, double targetZ,
        float progress, int seed, int yHint
) implements CustomPacketPayload {

    public static final Type<IceSpikeTrailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_spike_trail"));

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
                    buf.readInt(), buf.readInt()
            )
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
