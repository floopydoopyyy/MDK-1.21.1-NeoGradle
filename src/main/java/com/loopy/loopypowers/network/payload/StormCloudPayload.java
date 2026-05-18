package com.loopy.loopypowers.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent every 6 ticks while the Lightning Maelstrom ultimate is active,
 * and once at activation.
 *
 * @param entityId      Caster's entity ID — client looks up their position.
 * @param stormTicks    Remaining storm ticks, used as a rotation angle seed.
 * @param isActivation  True only on the very first send (activation burst).
 */
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}