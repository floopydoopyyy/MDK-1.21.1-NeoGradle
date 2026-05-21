package com.loopy.loopypowers.client;

import com.loopy.loopypowers.client.fx.*;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.network.AbilityPackets;
import com.loopy.loopypowers.network.ClientPowerState;
import com.loopy.loopypowers.network.payload.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.lwjgl.glfw.GLFW;

public class LoopypowersClient {

    public static KeyMapping PRIMARY_ABILITY_KEY;
    public static KeyMapping SECONDARY_ABILITY_KEY;
    public static KeyMapping ULTIMATE_ABILITY_KEY;
    public static KeyMapping TOGGLE_PASSIVE_KEY;

    // ============================================================
    // MOD BUS (Initialization, Rendering, Keys, Networking)
    // ============================================================
    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = "loopypowers", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {

        @SubscribeEvent
        @SuppressWarnings({"rawtypes", "unchecked"})
        public static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
            for (var skinName : event.getSkins()) {
                var renderer = event.getSkin(skinName);

                // Cast the base renderer to a LivingEntityRenderer so we can access addLayer()
                if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer livingRenderer) {
                    livingRenderer.addLayer(new com.loopy.loopypowers.client.render.WingsOfValorLayer(livingRenderer, event.getEntityModels()));
                }
            }
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            // entity rendering
            event.registerEntityRenderer(ModEntities.POWER_FIREBALL.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(ModEntities.BLOOD_CLOT.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.SONIC_BOLT.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.SHADOW_STEP.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.COMPEL_ENTITY.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.PUPPETRY_ENTITY.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.BLACK_HOLE_ENTITY.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.DISPLACE_ENTITY.get(), NoopRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            System.out.println("Loopypowers client loaded");
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            PRIMARY_ABILITY_KEY = new KeyMapping("key.loopypowers.primary", GLFW.GLFW_KEY_G, "category.loopypowers");
            SECONDARY_ABILITY_KEY = new KeyMapping("key.loopypowers.secondary", GLFW.GLFW_KEY_F, "category.loopypowers");
            ULTIMATE_ABILITY_KEY = new KeyMapping("key.loopypowers.ultimate", GLFW.GLFW_KEY_Q, "category.loopypowers");
            TOGGLE_PASSIVE_KEY = new KeyMapping("key.loopypowers.toggle_passive", GLFW.GLFW_KEY_APOSTROPHE, "category.loopypowers");

            event.register(PRIMARY_ABILITY_KEY);
            event.register(SECONDARY_ABILITY_KEY);
            event.register(ULTIMATE_ABILITY_KEY);
            event.register(TOGGLE_PASSIVE_KEY);
        }

        @SubscribeEvent
        public static void registerNetwork(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar("loopypowers").versioned("1.0.0");

            // strength
            registrar.playToClient(AbilityPackets.SyncStrengthPayload.ID, AbilityPackets.SyncStrengthPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientPowerState.setStrengthPower(p.hasStrength())));
            // hidden players
            registrar.playToClient(AbilityPackets.HidePlayerPayload.ID, AbilityPackets.HidePlayerPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> HiddenPlayersClient.hide(p.entityId(), p.ticks())));
            // camerashake
            registrar.playToClient(AbilityPackets.CameraShakePayload.ID, AbilityPackets.CameraShakePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> com.loopy.loopypowers.client.fx.CameraShakeClient.start(p.ticks(), p.strength())));
            // stun audio
            registrar.playToClient(AbilityPackets.StunAudioPayload.ID, AbilityPackets.StunAudioPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> StunAudioClient.setStun(p.ticks())));
            // resonance (sound
            registrar.playToClient(AbilityPackets.ResonanceTrailPayload.ID, AbilityPackets.ResonanceTrailPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> SoundFX.addTrailPoints(p.targetId(), p.count(), p.intensity())));
            registrar.playToClient(AbilityPackets.ResonanceRingPayload.ID, AbilityPackets.ResonanceRingPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> SoundFX.spawnRing(p.targetId(), p.intensity())));
            registrar.playToClient(AbilityPackets.ResonanceLinePayload.ID, AbilityPackets.ResonanceLinePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> SoundFX.setLine(p.targetId(), p.ticks())));
            // flight
            registrar.playToClient(FlightBoomWindupPayload.TYPE, FlightBoomWindupPayload.STREAM_CODEC, ClientPayloadHandler::handleFlightBoomWindup);
            registrar.playToClient(FlightBoomDashPayload.TYPE, FlightBoomDashPayload.STREAM_CODEC, ClientPayloadHandler::handleFlightBoomDash);
            registrar.playToClient(FlightBoomImpactPayload.TYPE, FlightBoomImpactPayload.STREAM_CODEC, ClientPayloadHandler::handleFlightBoomImpact);
            // healing
            registrar.playToClient(HealingUltPayload.TYPE, HealingUltPayload.STREAM_CODEC, ClientPayloadHandler::handleHealingUlt);
            // gravity
            registrar.playToClient(BlackHoleParticlePayload.TYPE, BlackHoleParticlePayload.STREAM_CODEC, ClientPayloadHandler::handleBlackHoleParticles);
            // fate
            registrar.playToClient(FateAuraPayload.TYPE, FateAuraPayload.STREAM_CODEC, ClientPayloadHandler::handleFateAura);
            // blood
            registrar.playToClient(BloodWhipPayload.TYPE, BloodWhipPayload.STREAM_CODEC, ClientPayloadHandler::handleBloodWhip);
            registrar.playToClient(BloodBindPayload.TYPE, BloodBindPayload.STREAM_CODEC, ClientPayloadHandler::handleBloodBind);
            // dimensional
            registrar.playToClient(BlackoutFxPayload.TYPE, BlackoutFxPayload.STREAM_CODEC, ClientPayloadHandler::handleBlackoutFx);
            registrar.playToClient(FractureFxPayload.TYPE, FractureFxPayload.STREAM_CODEC, ClientPayloadHandler::handleFractureFx);
            // explosion
            registrar.playToClient(ExplosionDropZonePayload.TYPE, ExplosionDropZonePayload.STREAM_CODEC, ClientPayloadHandler::handleExplosionDropZone);
            // fire
            registrar.playToClient(FireUltPayload.TYPE, FireUltPayload.STREAM_CODEC, ClientPayloadHandler::handleFireUltFx);
            // fortune
            registrar.playToClient(DuelBeamPayload.TYPE, DuelBeamPayload.STREAM_CODEC, ClientPayloadHandler::handleDuelBeam);
            registrar.playToClient(DuelTetherPayload.TYPE, DuelTetherPayload.STREAM_CODEC, ClientPayloadHandler::handleDuelTether);
            registrar.playToClient(DuelAuraPayload.TYPE, DuelAuraPayload.STREAM_CODEC, ClientPayloadHandler::handleDuelAura);
            registrar.playToClient(HouseRoofFxPayload.TYPE, HouseRoofFxPayload.STREAM_CODEC, ClientPayloadHandler::handleHouseRoofFx);
            // lightning
            registrar.playToClient(StormCloudPayload.TYPE, StormCloudPayload.STREAM_CODEC, ClientPayloadHandler::handleStormCloud);
            //nature
            registrar.playToClient(NatureCageFxPayload.TYPE, NatureCageFxPayload.STREAM_CODEC, ClientPayloadHandler::handleNatureCageFx);
            registrar.playToClient(NatureGasFxPayload.TYPE, NatureGasFxPayload.STREAM_CODEC, ClientPayloadHandler::handleNatureGasFx);
            registrar.playToClient(NatureTetherFxPayload.TYPE, NatureTetherFxPayload.STREAM_CODEC, ClientPayloadHandler::handleNatureTetherFx);
            // ice
            registrar.playToClient(IceSpikeTrailPayload.TYPE, IceSpikeTrailPayload.STREAM_CODEC, ClientPayloadHandler::handleIceSpikeTrail);
            registrar.playToClient(IceSpikePuffPayload.TYPE, IceSpikePuffPayload.STREAM_CODEC, ClientPayloadHandler::handleIceSpikePuff);
            registrar.playToClient(IceFreezeStagePayload.TYPE, IceFreezeStagePayload.STREAM_CODEC, ClientPayloadHandler::handleIceFreezeStage);
            registrar.playToClient(IceShatterPayload.TYPE, IceShatterPayload.STREAM_CODEC, ClientPayloadHandler::handleIceShatter);
            registrar.playToClient(IceBeamChargePayload.TYPE, IceBeamChargePayload.STREAM_CODEC, ClientPayloadHandler::handleIceBeamCharge);
            registrar.playToClient(IceBeamFirePayload.TYPE, IceBeamFirePayload.STREAM_CODEC, ClientPayloadHandler::handleIceBeamFire);
            registrar.playToClient(IceBlizzardPayload.TYPE, IceBlizzardPayload.STREAM_CODEC, ClientPayloadHandler::handleIceBlizzard);
            // psychic
            registrar.playToClient(PuppetryTickPayload.TYPE, PuppetryTickPayload.CODEC, ClientPayloadHandler::handlePuppetryTick);
            registrar.playToClient(PuppetryHitPayload.TYPE, PuppetryHitPayload.CODEC, ClientPayloadHandler::handlePuppetryHit);
            registrar.playToClient(CompelTickPayload.TYPE, CompelTickPayload.CODEC, ClientPayloadHandler::handleCompelTick);
            registrar.playToClient(CompelHitPayload.TYPE, CompelHitPayload.CODEC, ClientPayloadHandler::handleCompelHit);
            registrar.playToClient(CompelAuraPayload.TYPE, CompelAuraPayload.CODEC, ClientPayloadHandler::handleCompelAura);
            registrar.playToClient(SpikeAuraPayload.TYPE, SpikeAuraPayload.CODEC, ClientPayloadHandler::handleSpikeAura);
            registrar.playToClient(PossessedAuraPayload.TYPE, PossessedAuraPayload.CODEC, ClientPayloadHandler::handlePossessedAura);
            registrar.playToClient(PsychicSpikeImpactPayload.TYPE, PsychicSpikeImpactPayload.CODEC, ClientPayloadHandler::handleSpikeImpact);
            registrar.playToClient(PsychicSpikeBeamPayload.TYPE, PsychicSpikeBeamPayload.CODEC, ClientPayloadHandler::handleSpikeBeam);
            registrar.playToClient(PsychicLeechPayload.TYPE, PsychicLeechPayload.CODEC, ClientPayloadHandler::handleLeech);
            // lightning
            registrar.playToClient(LightningClapPayload.TYPE, LightningClapPayload.CODEC, ClientPayloadHandler::handleLightningClap);
            registrar.playToClient(LightningClapHitPayload.TYPE, LightningClapHitPayload.CODEC, ClientPayloadHandler::handleLightningClapHit);
            registrar.playToClient(LightningSuperchargeBurstPayload.TYPE, LightningSuperchargeBurstPayload.CODEC, ClientPayloadHandler::handleLightningSuperchargeBurst);
            registrar.playToClient(LightningSuperchargeAuraPayload.TYPE, LightningSuperchargeAuraPayload.CODEC, ClientPayloadHandler::handleLightningSuperchargeAura);
            // speed
            registrar.playToClient(SpeedPinballAnchorPayload.TYPE, SpeedPinballAnchorPayload.CODEC, ClientPayloadHandler::handleSpeedPinballAnchor);
            registrar.playToClient(SpeedDashCastPayload.TYPE, SpeedDashCastPayload.CODEC, ClientPayloadHandler::handleSpeedDashCast);
            registrar.playToClient(SpeedDashTrailPayload.TYPE, SpeedDashTrailPayload.CODEC, ClientPayloadHandler::handleSpeedDashTrail);
            registrar.playToClient(SpeedOverdriveCastPayload.TYPE, SpeedOverdriveCastPayload.CODEC, ClientPayloadHandler::handleSpeedOverdriveCast);
            registrar.playToClient(SpeedOverdriveTrailPayload.TYPE, SpeedOverdriveTrailPayload.CODEC, ClientPayloadHandler::handleSpeedOverdriveTrail);
            registrar.playToClient(SpeedLowHealthBurstPayload.TYPE, SpeedLowHealthBurstPayload.CODEC, ClientPayloadHandler::handleSpeedLowHealthBurst);
            registrar.playToClient(SpeedExplosionFxPayload.TYPE, SpeedExplosionFxPayload.CODEC, ClientPayloadHandler::handleSpeedExplosionFx);
            // sound
            registrar.playToClient(SoundBassPullPayload.TYPE, SoundBassPullPayload.STREAM_CODEC, ClientPayloadHandler::handleSoundBassPull);
            registrar.playToClient(SoundBassBurstPayload.TYPE, SoundBassBurstPayload.STREAM_CODEC, ClientPayloadHandler::handleSoundBassBurst);
            registrar.playToClient(SoundBassPullAnimatePayload.TYPE, SoundBassPullAnimatePayload.STREAM_CODEC, ClientPayloadHandler::handleSoundBassPullAnimate);
            registrar.playToClient(SoundBassBlastAnimatePayload.TYPE, SoundBassBlastAnimatePayload.STREAM_CODEC, ClientPayloadHandler::handleSoundBassBlastAnimate);
            registrar.playToClient(SoundUltimateBeamPayload.TYPE, SoundUltimateBeamPayload.STREAM_CODEC, ClientPayloadHandler::handleSoundUltimateBeam);
            registrar.playToClient(SoundBassPulsePayload.TYPE, SoundBassPulsePayload.STREAM_CODEC, ClientPayloadHandler::handleSoundBassPulse);
            // strength
            registrar.playToClient(StrengthParticlePayload.TYPE, StrengthParticlePayload.STREAM_CODEC, ClientPayloadHandler::handleStrengthParticles);
            // teleport
            registrar.playToClient(TeleportParticlePayload.TYPE, TeleportParticlePayload.STREAM_CODEC, ClientPayloadHandler::handleTeleportParticles);
            // telekinesis
            registrar.playToClient(TelekinesisParticlePayload.TYPE, TelekinesisParticlePayload.STREAM_CODEC, ClientPayloadHandler::handleTelekinesisParticles);
            // ritual
            registrar.playToClient(ElementalRitualTickPayload.TYPE, ElementalRitualTickPayload.STREAM_CODEC, ClientPayloadHandler::handleElementalRitual);
        }
    }

    // ============================================================
    // GAME BUS (Tick Events)
    // ============================================================
    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = "loopypowers", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ClientGameBusEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            // Flight Glide Check
            if (client.options.keyJump.consumeClick()) {
                if (!client.player.onGround() && !client.player.isFallFlying() && client.player.getDeltaMovement().y <= -0.08) {
                    PacketDistributor.sendToServer(new AbilityPackets.FlightGlidePayload());
                }
            }

            // Keys Check
            while (PRIMARY_ABILITY_KEY.consumeClick()) sendPrimaryAbility();
            while (SECONDARY_ABILITY_KEY.consumeClick()) sendSecondaryAbility();
            while (ULTIMATE_ABILITY_KEY.consumeClick()) sendUltimateAbility();
            while (TOGGLE_PASSIVE_KEY.consumeClick()) sendTogglePassive();

            HiddenPlayersClient.tick();
            StunAudioClient.tick();
            SoundFX.tick(client);
            FateAuraClient.tick(client);
            BloodFxClient.tick(client);
            DimensionalFxClient.tick(client);
            ExplosionFxClient.tick(client);
            FireFxClient.tick(client);
        }
    }

    // sending payloads
    public static void sendPrimaryAbility() {
        PacketDistributor.sendToServer(new AbilityPackets.PrimaryAbilityPayload());
    }

    public static void sendSecondaryAbility() {
        PacketDistributor.sendToServer(new AbilityPackets.SecondaryAbilityPayload());
    }

    public static void sendUltimateAbility() {
        PacketDistributor.sendToServer(new AbilityPackets.UltimateAbilityPayload());
    }

    public static void sendTogglePassive() {
        PacketDistributor.sendToServer(new AbilityPackets.TogglePassivePayload());
    }
}