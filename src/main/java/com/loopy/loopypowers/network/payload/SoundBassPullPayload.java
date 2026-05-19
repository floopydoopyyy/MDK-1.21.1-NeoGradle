package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SoundBassPullPayload(
        Vec3 center,
        long seed
) implements CustomPacketPayload {

    public static final Type<SoundBassPullPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "sound_bass_pull"));

    public static final StreamCodec<FriendlyByteBuf, SoundBassPullPayload> STREAM_CODEC =
            StreamCodec.of(
                    SoundBassPullPayload::encode,
                    SoundBassPullPayload::decode
            );

    private static void encode(FriendlyByteBuf buf, SoundBassPullPayload payload) {
        buf.writeVec3(payload.center);
        buf.writeLong(payload.seed);
    }

    private static SoundBassPullPayload decode(FriendlyByteBuf buf) {
        return new SoundBassPullPayload(
                buf.readVec3(),
                buf.readLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}