package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record FlightBoomDashPayload(int entityId, Vec3 dir, boolean isStart) implements CustomPacketPayload {
    public static final Type<FlightBoomDashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "flight_boom_dash"));

    public static final StreamCodec<FriendlyByteBuf, FlightBoomDashPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeDouble(payload.dir().x);
                buf.writeDouble(payload.dir().y);
                buf.writeDouble(payload.dir().z);
                buf.writeBoolean(payload.isStart());
            },
            buf -> new FlightBoomDashPayload(
                    buf.readInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}