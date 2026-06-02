package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record NatureTetherFxPayload(Vec3 from, Vec3 to, int seed) implements CustomPacketPayload {
    public static final Type<NatureTetherFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "nature_tether_fx"));
    public static final StreamCodec<FriendlyByteBuf, NatureTetherFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVec3(p.from()); buf.writeVec3(p.to()); buf.writeInt(p.seed()); },
            buf -> new NatureTetherFxPayload(buf.readVec3(), buf.readVec3(), buf.readInt()));
    static { ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleNatureTetherFx); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}