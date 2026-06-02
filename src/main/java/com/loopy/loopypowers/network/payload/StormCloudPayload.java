package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StormCloudPayload(int entityId, int stormTicks, boolean isActivation)
        implements CustomPacketPayload {

    public static final Type<StormCloudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "storm_cloud"));

    public static final StreamCodec<FriendlyByteBuf, StormCloudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.entityId());
                buf.writeShort(p.stormTicks());
                buf.writeBoolean(p.isActivation());
            },
            buf -> new StormCloudPayload(buf.readInt(), buf.readShort(), buf.readBoolean())
    );

    // ── Self-registration ──────────────────────────────────────────────────────
    static {
        ClientPayloadRegistry.add(r -> r.playToClient(TYPE, STREAM_CODEC, (payload, ctx) -> {}));
    }
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}