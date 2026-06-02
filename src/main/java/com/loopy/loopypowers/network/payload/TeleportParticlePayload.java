package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TeleportParticlePayload(int eventId, double x1, double y1, double z1, double x2, double y2, double z2) implements CustomPacketPayload {

    public static final Type<TeleportParticlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "teleport_particles"));

    public static final StreamCodec<FriendlyByteBuf, TeleportParticlePayload> STREAM_CODEC = StreamCodec.ofMember(
            TeleportParticlePayload::write,
            TeleportParticlePayload::new
    );

    public TeleportParticlePayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(eventId);
        buf.writeDouble(x1);
        buf.writeDouble(y1);
        buf.writeDouble(z1);
        buf.writeDouble(x2);
        buf.writeDouble(y2);
        buf.writeDouble(z2);
    }

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleTeleportParticles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final int DODGE         = 0;
    public static final int BLINK         = 1;
    public static final int SWAP_WHIFF    = 2;
    public static final int SWAP_HIT      = 3;
    public static final int FRENZY_TICK   = 4;
    public static final int FRENZY_STRIKE = 5;
}