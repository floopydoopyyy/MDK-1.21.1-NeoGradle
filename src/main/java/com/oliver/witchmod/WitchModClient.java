package com.oliver.witchmod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.blocks.WitchModFluids;
import com.oliver.witchmod.client.ChatOverlayLayer;
import com.oliver.witchmod.client.GluttonyHudLayer;
import com.oliver.witchmod.client.LoadingScreenOverlay;
import com.oliver.witchmod.client.ThirstHudLayer;
import com.oliver.witchmod.ui.BewitchingTableScreen;
import com.oliver.witchmod.ui.WitchModMenus;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = WitchMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public class WitchModClient {
    public WitchModClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        WitchMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        WitchMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(WitchModMenus.BEWITCHING_TABLE.get(), BewitchingTableScreen::new);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // WitchMod's stacked right-side bars sit above the vanilla hunger bar (Phase D / Section 13.4);
        // HudBars keeps them from overlapping when several are active at once.
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "gluttony_bar"), new GluttonyHudLayer());
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "thirst_bar"), new ThirstHudLayer());
        // Chat blessing's Twitch overlay (a side panel, above the HUD but below any fullscreen overlay).
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "chat_overlay"), new ChatOverlayLayer());
        // Loading Screen prank overlay sits above everything (it's a fake fullscreen loading screen).
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "loading_screen"), new LoadingScreenOverlay());
    }

    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WitchModFluids.WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WitchModFluids.WATER_FLOW;
            }

            @Override
            public int getTintColor() {
                return WitchModFluids.TINT_COLOR;
            }
        }, WitchModFluids.PURIFYING_WATER_TYPE.get());
    }
}
