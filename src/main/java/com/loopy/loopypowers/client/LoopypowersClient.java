package com.loopy.loopypowers.client;

import com.loopy.loopypowers.client.fx.*;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.item.ModItems;
import com.loopy.loopypowers.network.AbilityPackets;
import com.loopy.loopypowers.network.ClientPowerState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
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
        public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                private static final ResourceLocation WINGS_TEXTURE =
                        ResourceLocation.fromNamespaceAndPath("loopypowers", "textures/entity/wings_of_valor.png");

                public ResourceLocation getCustomElytraTexture(ItemStack stack, LivingEntity entity) {
                    return WINGS_TEXTURE;
                }
            }, ModItems.WINGS_OF_VALOR.get());
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

            registrar.playToClient(
                    AbilityPackets.SyncStrengthPayload.ID,
                    AbilityPackets.SyncStrengthPayload.CODEC,
                    (AbilityPackets.SyncStrengthPayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            ClientPowerState.setStrengthPower(payload.hasStrength()))
            );

            registrar.playToClient(
                    AbilityPackets.HidePlayerPayload.ID,
                    AbilityPackets.HidePlayerPayload.CODEC,
                    (AbilityPackets.HidePlayerPayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            HiddenPlayersClient.hide(payload.entityId(), payload.ticks()))
            );

            registrar.playToClient(
                    AbilityPackets.CameraShakePayload.ID,
                    AbilityPackets.CameraShakePayload.CODEC,
                    (AbilityPackets.CameraShakePayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            com.loopy.loopypowers.client.fx.CameraShakeClient.start(payload.ticks(), payload.strength()))
            );

            registrar.playToClient(
                    AbilityPackets.StunAudioPayload.ID,
                    AbilityPackets.StunAudioPayload.CODEC,
                    (AbilityPackets.StunAudioPayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            StunAudioClient.setStun(payload.ticks()))
            );

            registrar.playToClient(
                    AbilityPackets.ResonanceTrailPayload.ID,
                    AbilityPackets.ResonanceTrailPayload.CODEC,
                    (AbilityPackets.ResonanceTrailPayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            SoundFX.addTrailPoints(payload.targetId(), payload.count(), payload.intensity())
                    )
            );

            registrar.playToClient(
                    AbilityPackets.ResonanceRingPayload.ID,
                    AbilityPackets.ResonanceRingPayload.CODEC,
                    (AbilityPackets.ResonanceRingPayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            SoundFX.spawnRing(payload.targetId(), payload.intensity())
                    )
            );

            registrar.playToClient(
                    AbilityPackets.ResonanceLinePayload.ID,
                    AbilityPackets.ResonanceLinePayload.CODEC,
                    (AbilityPackets.ResonanceLinePayload payload, IPayloadContext context) -> context.enqueueWork(() ->
                            SoundFX.setLine(payload.targetId(), payload.ticks())
                    )
            );
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