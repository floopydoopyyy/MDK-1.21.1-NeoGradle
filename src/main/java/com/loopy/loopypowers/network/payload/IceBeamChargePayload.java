package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceBeamChargePayload(int entityId, int tickCount) implements CustomPacketPayload {

    public static final Type<IceBeamChargePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "ice_beam_charge"));

    public static final StreamCodec<FriendlyByteBuf, IceBeamChargePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId()); buf.writeInt(p.tickCount()); },
            buf -> new IceBeamChargePayload(buf.readInt(), buf.readInt())
    );

    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}