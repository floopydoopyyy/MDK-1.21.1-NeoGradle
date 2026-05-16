package com.loopy.loopypowers.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FateAuraPayload(int entityId, float storedDamage, int timerTicks) implements CustomPacketPayload {

    public static final Type<FateAuraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "fate_aura"));

    public static final StreamCodec<ByteBuf, FateAuraPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FateAuraPayload::entityId,
            ByteBufCodecs.FLOAT, FateAuraPayload::storedDamage,
            ByteBufCodecs.INT, FateAuraPayload::timerTicks,
            FateAuraPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}