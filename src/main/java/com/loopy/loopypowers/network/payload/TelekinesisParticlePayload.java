package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TelekinesisParticlePayload(int eventId, int entityId, double x, double y, double z, double tx, double ty, double tz, float data) implements CustomPacketPayload {

    public static final Type<TelekinesisParticlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "telekinesis_particles"));

    public static final StreamCodec<FriendlyByteBuf, TelekinesisParticlePayload> STREAM_CODEC = StreamCodec.ofMember(
            TelekinesisParticlePayload::write,
            TelekinesisParticlePayload::new
    );

    public TelekinesisParticlePayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(eventId);
        buf.writeInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(tx);
        buf.writeDouble(ty);
        buf.writeDouble(tz);
        buf.writeFloat(data);
    }

    static {
        ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleTelekinesisParticles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final int YANK_CAST        = 0;
    public static final int SUSPEND_CAST     = 1;
    public static final int IMPACT_RING      = 2;
    public static final int SUSPEND_TICK     = 3;
    public static final int CHOKE_TICK       = 4;
    public static final int WALL_IMPACT      = 5;
    public static final int FLOOR_IMPACT     = 6;
    public static final int SLAM_IMPACT      = 7;
    public static final int ULTIMATE_CAST    = 8;
    public static final int DEBRIS_HARVEST   = 9;
    public static final int DEBRIS_TICK      = 10;
    public static final int PULL_TICK        = 11;
    public static final int ORBIT_TICK       = 12;
    public static final int PASSIVE_HIT      = 13;
    public static final int DEBRIS_EXPLOSION = 14;
    public static final int YANK_FALL_DAMAGE = 15;
    public static final int THROW_RELEASE    = 16;
    public static final int DEBRIS_THROW     = 17;
}