package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record NatureGasFxPayload(Vec3 center, float radius, float halfHeight, int count, int seed) implements CustomPacketPayload {
    public static final Type<NatureGasFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "nature_gas_fx"));

    public static final StreamCodec<FriendlyByteBuf, NatureGasFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVec3(payload.center());
                buf.writeFloat(payload.radius());
                buf.writeFloat(payload.halfHeight());
                buf.writeInt(payload.count());
                buf.writeInt(payload.seed());
            },
            buf -> new NatureGasFxPayload(buf.readVec3(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}