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
       Each static block registers into ClientPayloadRegistry with
       the REAL handler. The server never invokes playToClient
       handlers so client class references in lambdas are safe.
       One registration, no double-registration possible.
       ============================================================ */

    public record CameraShakePayload(int ticks, float strength) implements CustomPacketPayload {
        public static final Type<CameraShakePayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "camera_shake"));
        public static final StreamCodec<ByteBuf, CameraShakePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   CameraShakePayload::ticks,
                ByteBufCodecs.FLOAT, CameraShakePayload::strength,
                CameraShakePayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.CameraShakeClient.start(p.ticks(), p.strength())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SyncStrengthPayload(boolean hasStrength) implements CustomPacketPayload {
        public static final Type<SyncStrengthPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "sync_strength_power"));
        public static final StreamCodec<ByteBuf, SyncStrengthPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncStrengthPayload::hasStrength,
                SyncStrengthPayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.network.ClientPowerState.setStrengthPower(p.hasStrength())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record HidePlayerPayload(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<HidePlayerPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "hide_player"));
        public static final StreamCodec<ByteBuf, HidePlayerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, HidePlayerPayload::entityId,
                ByteBufCodecs.INT, HidePlayerPayload::ticks,
                HidePlayerPayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.HiddenPlayersClient.hide(p.entityId(), p.ticks())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record StunAudioPayload(int ticks) implements CustomPacketPayload {
        public static final Type<StunAudioPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "stun_audio"));
        public static final StreamCodec<ByteBuf, StunAudioPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, StunAudioPayload::ticks,
                StunAudioPayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.StunAudioClient.setStun(p.ticks())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResonanceTrailPayload(int targetId, int count, float intensity) implements CustomPacketPayload {
        public static final Type<ResonanceTrailPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "resonance_trail"));
        public static final StreamCodec<ByteBuf, ResonanceTrailPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   ResonanceTrailPayload::targetId,
                ByteBufCodecs.INT,   ResonanceTrailPayload::count,
                ByteBufCodecs.FLOAT, ResonanceTrailPayload::intensity,
                ResonanceTrailPayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.SoundFX.addTrailPoints(p.targetId(), p.count(), p.intensity())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResonanceRingPayload(int targetId, float intensity) implements CustomPacketPayload {
        public static final Type<ResonanceRingPayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "resonance_ring"));
        public static final StreamCodec<ByteBuf, ResonanceRingPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   ResonanceRingPayload::targetId,
                ByteBufCodecs.FLOAT, ResonanceRingPayload::intensity,
                ResonanceRingPayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.SoundFX.spawnRing(p.targetId(), p.intensity())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResonanceLinePayload(int targetId, int ticks, float intensity) implements CustomPacketPayload {
        public static final Type<ResonanceLinePayload> ID =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "resonance_line"));
        public static final StreamCodec<ByteBuf, ResonanceLinePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT,   ResonanceLinePayload::targetId,
                ByteBufCodecs.INT,   ResonanceLinePayload::ticks,
                ByteBufCodecs.FLOAT, ResonanceLinePayload::intensity,
                ResonanceLinePayload::new);
        static {
            ClientPayloadRegistry.add(ID, CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.SoundFX.setLine(p.targetId(), p.ticks())));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /* ============================================================
       REGISTRATION — single event handler, no dist split needed.
       ClientPayloadRegistry.registerAll() registers every channel
       exactly once with its real handler already embedded.
       ============================================================ */

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Loopypowers.MOD_ID).versioned("1");

        // ── Server-bound ───────────────────────────────────────────────────
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
                (payload, context) -> context.enqueueWork(() ->
                        context.player().getTags().add("fl_glide_req")));

        // ── Client-bound: one registration each, real handler embedded ─────
        ClientPayloadRegistry.registerAll(registrar);
    }
}