package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SoundBassBurstPayload(
        Vec3 center,
        float radius,
        long seed
) implements CustomPacketPayload {

    public static final Type<SoundBassBurstPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "sound_bass_burst"));

    public static final StreamCodec<FriendlyByteBuf, SoundBassBurstPayload> STREAM_CODEC =
            StreamCodec.of(
                    SoundBassBurstPayload::encode,
                    SoundBassBurstPayload::decode
            );

    private static void encode(FriendlyByteBuf buf, SoundBassBurstPayload payload) {
        buf.writeVec3(payload.center);
        buf.writeFloat(payload.radius);
        buf.writeLong(payload.seed);
    }

    private static SoundBassBurstPayload decode(FriendlyByteBuf buf) {
        return new SoundBassBurstPayload(buf.readVec3(), buf.readFloat(), buf.readLong());
    }

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleSoundBassBurst);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}