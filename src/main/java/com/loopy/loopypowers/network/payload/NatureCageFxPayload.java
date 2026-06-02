package com.loopy.loopypowers.network.payload;

import com.loopy.loopypowers.client.fx.ClientPayloadHandler;
import com.loopy.loopypowers.network.ClientPayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NatureCageFxPayload(int cx, int cy, int cz, int radius, int thickness) implements CustomPacketPayload {
    public static final Type<NatureCageFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("loopypowers", "nature_cage_fx"));
    public static final StreamCodec<FriendlyByteBuf, NatureCageFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.cx()); buf.writeInt(p.cy()); buf.writeInt(p.cz()); buf.writeInt(p.radius()); buf.writeInt(p.thickness()); },
            buf -> new NatureCageFxPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
    static { ClientPayloadRegistry.add(TYPE, STREAM_CODEC, ClientPayloadHandler::handleNatureCageFx); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}