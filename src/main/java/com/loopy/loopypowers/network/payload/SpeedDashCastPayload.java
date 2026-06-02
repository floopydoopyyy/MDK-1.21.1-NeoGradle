package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Fired once per dash cast (and once per pinball launch).
 * Carries the player's eye position and normalised look vector so the
 * client can reproduce the burst + backward trail arc.
 */
public record SpeedDashCastPayload(
        double x, double y, double z,
        double lookX, double lookY, double lookZ
) implements CustomPacketPayload {

    public static final Type<SpeedDashCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "speed_dash_cast"));

    public static final StreamCodec<FriendlyByteBuf, SpeedDashCastPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.x);     buf.writeDouble(p.y);     buf.writeDouble(p.z);
                        buf.writeDouble(p.lookX); buf.writeDouble(p.lookY); buf.writeDouble(p.lookZ);
                    },
                    buf -> new SpeedDashCastPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble()
                    )
            );

    static {
        ClientPayloadRegistry.add(TYPE, CODEC, ClientPayloadHandler::handleSpeedDashCast);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}