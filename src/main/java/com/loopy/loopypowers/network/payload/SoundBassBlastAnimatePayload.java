package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SoundBassBlastAnimatePayload(
        Vec3 center,
        int step,
        long seed
) implements CustomPacketPayload {

    public static final Type<SoundBassBlastAnimatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "sound_bass_blast_animate"));

    public static final StreamCodec<FriendlyByteBuf, SoundBassBlastAnimatePayload> STREAM_CODEC =
            StreamCodec.of(
                    SoundBassBlastAnimatePayload::encode,
                    SoundBassBlastAnimatePayload::decode
            );

    private static void encode(FriendlyByteBuf buf, SoundBassBlastAnimatePayload payload) {
        buf.writeVec3(payload.center);
        buf.writeInt(payload.step);
        buf.writeLong(payload.seed);
    }

    private static SoundBassBlastAnimatePayload decode(FriendlyByteBuf buf) {
        return new SoundBassBlastAnimatePayload(
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