package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Random;

// THIS is why it didn't work before:
// You were modifying the player's physical rotation, which fights with the mouse!
// Now, we modify the Camera's rendering angles directly.

@EventBusSubscriber(modid = "loopypowers", value = Dist.CLIENT) // Ensure your modid matches here!
public final class CameraShakeClient {
    private static int remaining = 0;
    private static float strength = 0f;
    private static final Random RNG = new Random();

    private CameraShakeClient() {}

    public static void start(int ticks, float s) {
        // System.out.println("Camera shake received: " + ticks + " strength " + s);
        remaining = Math.max(remaining, ticks); // parameters for shake
        strength = Math.max(strength, s);
    }

    // Handle the countdown timer safely every client tick
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (remaining > 0) {
            remaining--;
            if (remaining <= 0) {
                strength = 0f; // reset strength when done to avoid lingering floating point issues
            }
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (remaining <= 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.isPaused()) return;

        // makes it fade out
        float fade = Mth.clamp(remaining / 10.0f, 0f, 1f);
        float amp = strength * fade;

        float yawJitter = (RNG.nextFloat() * 2f - 1f) * amp;
        float pitchJitter = (RNG.nextFloat() * 2f - 1f) * amp * 0.6f;

        // tiny bit of roll
        float rollJitter = (RNG.nextFloat() * 2f - 1f) * amp * 0.4f;

        // jitter
        event.setYaw(event.getYaw() + yawJitter);
        event.setPitch(event.getPitch() + pitchJitter);
        event.setRoll(event.getRoll() + rollJitter);
    }
}