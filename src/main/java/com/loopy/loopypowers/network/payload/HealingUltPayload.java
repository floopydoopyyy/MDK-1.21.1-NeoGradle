package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HealingUltPayload(int entityId, int phase, int ultTicks, boolean isPhaseChange)
        implements CustomPacketPayload {

    public static final Type<HealingUltPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "healing_ult"));

    public static final StreamCodec<FriendlyByteBuf, HealingUltPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.entityId());
                buf.writeByte(p.phase());
                buf.writeShort(p.ultTicks());
                buf.writeBoolean(p.isPhaseChange());
            },
            buf -> new HealingUltPayload(buf.readInt(), buf.readByte(), buf.readShort(), buf.readBoolean())
    );

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleHealingUlt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}