package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SoundBassPulsePayload(
        Vec3 pos
) implements CustomPacketPayload {

    public static final Type<SoundBassPulsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "sound_bass_pulse"));

    public static final StreamCodec<FriendlyByteBuf, SoundBassPulsePayload> STREAM_CODEC =
            StreamCodec.of(
                    SoundBassPulsePayload::encode,
                    SoundBassPulsePayload::decode
            );

    private static void encode(FriendlyByteBuf buf, SoundBassPulsePayload payload) {
        buf.writeVec3(payload.pos);
    }

    private static SoundBassPulsePayload decode(FriendlyByteBuf buf) {
        return new SoundBassPulsePayload(
                buf.readVec3()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}