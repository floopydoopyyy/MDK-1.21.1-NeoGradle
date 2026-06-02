package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SoundUltimateBeamPayload(
        Vec3 start,
        Vec3 end,
        long seed
) implements CustomPacketPayload {

    public static final Type<SoundUltimateBeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "sound_ultimate_beam"));

    public static final StreamCodec<FriendlyByteBuf, SoundUltimateBeamPayload> STREAM_CODEC =
            StreamCodec.of(
                    SoundUltimateBeamPayload::encode,
                    SoundUltimateBeamPayload::decode
            );

    private static void encode(FriendlyByteBuf buf, SoundUltimateBeamPayload payload) {
        buf.writeVec3(payload.start);
        buf.writeVec3(payload.end);
        buf.writeLong(payload.seed);
    }

    private static SoundUltimateBeamPayload decode(FriendlyByteBuf buf) {
        return new SoundUltimateBeamPayload(buf.readVec3(), buf.readVec3(), buf.readLong());
    }

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleSoundUltimateBeam);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}