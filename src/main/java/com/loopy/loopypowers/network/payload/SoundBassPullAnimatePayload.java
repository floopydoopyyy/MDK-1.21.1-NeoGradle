package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SoundBassPullAnimatePayload(
        Vec3 center,
        int step,
        long seed
) implements CustomPacketPayload {

    public static final Type<SoundBassPullAnimatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "sound_bass_pull_animate"));

    public static final StreamCodec<FriendlyByteBuf, SoundBassPullAnimatePayload> STREAM_CODEC =
            StreamCodec.of(
                    SoundBassPullAnimatePayload::encode,
                    SoundBassPullAnimatePayload::decode
            );

    private static void encode(FriendlyByteBuf buf, SoundBassPullAnimatePayload payload) {
        buf.writeVec3(payload.center);
        buf.writeInt(payload.step);
        buf.writeLong(payload.seed);
    }

    private static SoundBassPullAnimatePayload decode(FriendlyByteBuf buf) {
        return new SoundBassPullAnimatePayload(
                buf.readVec3(),
                buf.readInt(),
                buf.readLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}