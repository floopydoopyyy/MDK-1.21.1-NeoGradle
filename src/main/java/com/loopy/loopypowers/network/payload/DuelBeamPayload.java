package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record DuelBeamPayload(Vec3 start, Vec3 end) implements CustomPacketPayload {
    public static final Type<DuelBeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "duel_beam"));

    public static final StreamCodec<FriendlyByteBuf, DuelBeamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVec3(payload.start());
                buf.writeVec3(payload.end());
            },
            buf -> new DuelBeamPayload(buf.readVec3(), buf.readVec3())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}