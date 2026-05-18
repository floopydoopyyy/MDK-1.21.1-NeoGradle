package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record NatureTetherFxPayload(Vec3 from, Vec3 to, int seed) implements CustomPacketPayload {
    public static final Type<NatureTetherFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "nature_tether_fx"));

    public static final StreamCodec<FriendlyByteBuf, NatureTetherFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVec3(payload.from());
                buf.writeVec3(payload.to());
                buf.writeInt(payload.seed());
            },
            buf -> new NatureTetherFxPayload(buf.readVec3(), buf.readVec3(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}