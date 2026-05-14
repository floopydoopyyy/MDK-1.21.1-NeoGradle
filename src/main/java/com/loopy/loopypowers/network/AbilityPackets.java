package com.loopy.loopypowers.network;

import com.loopy.loopypowers.Loopypowers;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// @EventBusSubscriber auto-registers the @SubscribeEvent method below on the MOD bus.
// This class is responsible ONLY for server-bound (playToServer) registrations.
// Client-bound (playToClient) registrations live in LoopypowersClient.registerNetwork()
// so they are only touched on Dist.CLIENT and never registered twice.
@EventBusSubscriber(modid = Loopypowers.MOD_ID)
public class AbilityPackets {

    /* ============================================================
       SERVER-BOUND PAYLOADS (Client -> Server)
       ============================================================ */

    public record PrimaryAbilityPayload() implements CustomPacketPayload {
        public static final Type<PrimaryAbilityPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "primary_ability"));
        public static final StreamCodec<ByteBuf, PrimaryAbilityPayload> CODEC =
                StreamCodec.unit(new PrimaryAbilityPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SecondaryAbilityPayload() implements CustomPacketPayload {
        public static final Type<SecondaryAbilityPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "secondary_ability"));
        public static final StreamCodec<ByteBuf, SecondaryAbilityPayload> CODEC =
                StreamCodec.unit(new SecondaryAbilityPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record UltimateAbilityPayload() implements CustomPacketPayload {
        public static final Type<UltimateAbilityPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "ultimate_ability"));
        public static final StreamCodec<ByteBuf, UltimateAbilityPayload> CODEC =
                StreamCodec.unit(new UltimateAbilityPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record TogglePassivePayload() implements CustomPacketPayload {
        public static final Type<TogglePassivePayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "toggle_passive"));
        public static final StreamCodec<ByteBuf, TogglePassivePayload> CODEC =
                StreamCodec.unit(new TogglePassivePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record FlightGlidePayload() implements CustomPacketPayload {
        public static final Type<FlightGlidePayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "flight_glide_request"));
        public static final StreamCodec<ByteBuf, FlightGlidePayload> CODEC =
                StreamCodec.unit(new FlightGlidePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /* ============================================================
       CLIENT-BOUND PAYLOAD DEFINITIONS (Server -> Client)
       Handlers are registered in LoopypowersClient — definitions
       live here so both sides can reference the same types.
       ============================================================ */

    public record CameraShakePayload(int ticks, float strength) implements CustomPacketPayload {
        public static final Type<CameraShakePayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "camera_shake"));
        public static final StreamCodec<ByteBuf, CameraShakePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   CameraShakePayload::ticks,
                ByteBufCodecs.FLOAT, CameraShakePayload::strength,
                CameraShakePayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SyncStrengthPayload(boolean hasStrength) implements CustomPacketPayload {
        public static final Type<SyncStrengthPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "sync_strength_power"));
        public static final StreamCodec<ByteBuf, SyncStrengthPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncStrengthPayload::hasStrength,
                SyncStrengthPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record HidePlayerPayload(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<HidePlayerPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "hide_player"));
        public static final StreamCodec<ByteBuf, HidePlayerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, HidePlayerPayload::entityId,
                ByteBufCodecs.INT, HidePlayerPayload::ticks,
                HidePlayerPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record StunAudioPayload(int ticks) implements CustomPacketPayload {
        public static final Type<StunAudioPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "stun_audio"));
        public static final StreamCodec<ByteBuf, StunAudioPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, StunAudioPayload::ticks,
                StunAudioPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResonanceTrailPayload(int targetId, int count, float intensity) implements CustomPacketPayload {
        public static final Type<ResonanceTrailPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "resonance_trail"));
        public static final StreamCodec<ByteBuf, ResonanceTrailPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   ResonanceTrailPayload::targetId,
                ByteBufCodecs.INT,   ResonanceTrailPayload::count,
                ByteBufCodecs.FLOAT, ResonanceTrailPayload::intensity,
                ResonanceTrailPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResonanceRingPayload(int targetId, float intensity) implements CustomPacketPayload {
        public static final Type<ResonanceRingPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "resonance_ring"));
        public static final StreamCodec<ByteBuf, ResonanceRingPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   ResonanceRingPayload::targetId,
                ByteBufCodecs.FLOAT, ResonanceRingPayload::intensity,
                ResonanceRingPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResonanceLinePayload(int targetId, int ticks, float intensity) implements CustomPacketPayload {
        public static final Type<ResonanceLinePayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "resonance_line"));
        public static final StreamCodec<ByteBuf, ResonanceLinePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   ResonanceLinePayload::targetId,
                ByteBufCodecs.INT,   ResonanceLinePayload::ticks,
                ByteBufCodecs.FLOAT, ResonanceLinePayload::intensity,
                ResonanceLinePayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /* ============================================================
       REGISTRATION — SERVER-BOUND ONLY
       Client-bound payloads are registered in LoopypowersClient
       so this method is never called on Dist.CLIENT, preventing
       the double-registration crash.
       ============================================================ */

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // Version string must match the one used in LoopypowersClient exactly.
        final PayloadRegistrar registrar = event.registrar(Loopypowers.MOD_ID).versioned("1");

        registrar.playToServer(PrimaryAbilityPayload.ID, PrimaryAbilityPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        PowerManager.usePrimary((ServerPlayer) context.player())));

        registrar.playToServer(SecondaryAbilityPayload.ID, SecondaryAbilityPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        PowerManager.useSecondary((ServerPlayer) context.player())));

        registrar.playToServer(UltimateAbilityPayload.ID, UltimateAbilityPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        PowerManager.useUltimate((ServerPlayer) context.player())));

        registrar.playToServer(TogglePassivePayload.ID, TogglePassivePayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        PassiveManager.toggle((ServerPlayer) context.player())));

        registrar.playToServer(FlightGlidePayload.ID, FlightGlidePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    player.getTags().add("fl_glide_req");
                }));
    }
}