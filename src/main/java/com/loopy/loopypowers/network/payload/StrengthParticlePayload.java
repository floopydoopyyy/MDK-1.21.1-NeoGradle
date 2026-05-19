package com.loopy.loopypowers.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record StrengthParticlePayload(int eventId, double x, double y, double z, double dx, double dy, double dz, int stateId, boolean flag) implements CustomPacketPayload {

    public static final Type<StrengthParticlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "strength_particles"));

    public static final StreamCodec<FriendlyByteBuf, StrengthParticlePayload> STREAM_CODEC = StreamCodec.ofMember(
            StrengthParticlePayload::write,
            StrengthParticlePayload::new
    );

    public StrengthParticlePayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(eventId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dx);
        buf.writeDouble(dy);
        buf.writeDouble(dz);
        buf.writeInt(stateId);
        buf.writeBoolean(flag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Event IDs
    public static final int ONE_PUNCH = 0;
    public static final int RAGE_HIT = 1;
    public static final int GROUND_SLAM = 2;
    public static final int SLAM_TARGET_CRIT = 3;
    public static final int RUSH_CANCEL = 4;
    public static final int RUSH_HIT = 5;
    public static final int RUSH_CRASH = 6;
    public static final int RAGE_PULSE = 7;
    public static final int RUSH_TICK = 8;
    public static final int RAGE_TICK = 9;
}