package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlackHoleParticlePayload(int entityId, float lifeProgress) implements CustomPacketPayload {

    public static final Type<BlackHoleParticlePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "bh_particles"));

    public static final StreamCodec<ByteBuf, BlackHoleParticlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,   BlackHoleParticlePayload::entityId,
            ByteBufCodecs.FLOAT, BlackHoleParticlePayload::lifeProgress,
            BlackHoleParticlePayload::new
    );

    // ── Self-registration ──────────────────────────────────────────────────────
    // Runs once when this class is first loaded (triggered by PayloadInit.init()).
    // Tells both server and client that this channel exists; the actual handler
    // is wired in LoopypowersClient and does not need to be repeated here.
    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}