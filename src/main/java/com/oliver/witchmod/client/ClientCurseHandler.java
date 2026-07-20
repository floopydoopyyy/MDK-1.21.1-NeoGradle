package com.oliver.witchmod.client;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The client-side curses (Phase D), driven off auto-synced flags on the local player:
 * <ul>
 *   <li><b>Moonwalker</b> — reverses forward/back movement input.</li>
 *   <li><b>Screensaver</b> — bounces the OS game window around the monitor DVD-logo style (windowed only).</li>
 *   <li><b>Minor Inconvenience</b> — renames the OS window title to nonsense (visible in windowed mode).</li>
 *   <li><b>Pacing</b> — rolls the camera for a "dramatic moment" during its freeze window.</li>
 * </ul>
 * All of this is genuinely client-only; the server just flips the synced flags.
 *
 * <p>The window effects (Screensaver/Minor Inconvenience) require a movable/titled window — they no-op in
 * fullscreen. Screensaver un-maximizes the window first (a maximized window can't be repositioned).
 *
 * <p>DIAGNOSTIC: each curse announces itself once (action bar) when it activates client-side, so it's
 * obvious the client received the flag even if the visible effect depends on window mode. Remove these
 * one-liners once behaviour is confirmed.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class ClientCurseHandler {
    private static final String[] SILLY_TITLES = {
            "Notepad", "Minceraft", "definitely not a game", "please respond", "loading...",
            "Excel - Budget_final_FINAL_v3.xlsx", "System Update in Progress", "404: window not found",
    };
    private static final int RENAME_INTERVAL = 40;   // ticks between title changes (2s)
    private static final int WINDOW_SPEED_PX = 3;

    private static int tickCounter;
    private static double bounceVx = 1.0;
    private static double bounceVy = 1.0;
    private static boolean minorSeen;
    private static boolean screensaverSeen;
    private static boolean moonwalkerSeen;
    private static boolean pacingSeen;

    private ClientCurseHandler() {}

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity().getData(WitchModAttachments.MOONWALKER_ACTIVE) >= 0) {
            // Reverse forward/back only (leave strafing and look alone). forwardImpulse is what drives
            // motion; keep the up/down booleans consistent with it so sprint/idle logic agrees.
            Input input = event.getInput();
            input.forwardImpulse = -input.forwardImpulse;
            boolean up = input.up;
            input.up = input.down;
            input.down = up;
        }
    }

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        long end = mc.player.getData(WitchModAttachments.PACING_END_TICK);
        if (mc.level.getGameTime() < end) {
            double t = mc.level.getGameTime() + event.getPartialTick();
            event.setRoll((float) (Math.sin(t * 0.4) * 20.0)); // dramatic wobble
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        tickCounter++;
        Window window = mc.getWindow();

        // Minor Inconvenience — rename the OS window title immediately on activation, then on an interval.
        boolean minorActive = player.getData(WitchModAttachments.MINOR_INCONVENIENCE_ACTIVE) >= 0;
        if (minorActive) {
            if (!minorSeen || tickCounter % RENAME_INTERVAL == 0) {
                window.setTitle(SILLY_TITLES[player.getRandom().nextInt(SILLY_TITLES.length)]);
            }
            minorSeen = trackActive(player, true, minorSeen, "Minor Inconvenience active — check your window title bar (windowed mode).");
        } else if (minorSeen) {
            window.setTitle("Minecraft");
            minorSeen = false;
        }

        // Screensaver — bounce the OS window continuously while active (windowed only).
        boolean screensaverActive = player.getData(WitchModAttachments.SCREENSAVER_ACTIVE) >= 0;
        if (screensaverActive && !window.isFullscreen()) {
            bounceWindow(window);
        }
        screensaverSeen = trackActive(player, screensaverActive, screensaverSeen,
                "Screensaver active — window drifts around (windowed mode only; fullscreen no-ops).");

        // Activation announcements for the input/camera curses too (their effect is combat/movement based).
        moonwalkerSeen = trackActive(player, player.getData(WitchModAttachments.MOONWALKER_ACTIVE) >= 0,
                moonwalkerSeen, "Moonwalker active — forward is reversed.");
        pacingSeen = trackActive(player, mc.level.getGameTime() < player.getData(WitchModAttachments.PACING_END_TICK),
                pacingSeen, "Pacing — dramatic moment!");
    }

    /** Sends the action-bar message the first tick {@code active} becomes true; returns the new seen-state. */
    private static boolean trackActive(LocalPlayer player, boolean active, boolean seen, String message) {
        if (active && !seen) {
            player.displayClientMessage(Component.literal("[WitchMod] " + message), true);
        }
        return active;
    }

    private static void bounceWindow(Window window) {
        long handle = window.getWindow();
        GLFWVidMode mode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (mode == null) {
            return; // window manipulation unavailable — no-op per spec
        }
        // A maximized window can't be repositioned on some OSes — un-maximize so the bounce works.
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            GLFW.glfwRestoreWindow(handle);
        }

        int[] wx = new int[1];
        int[] wy = new int[1];
        GLFW.glfwGetWindowPos(handle, wx, wy);
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowSize(handle, ww, wh);

        int maxX = Math.max(0, mode.width() - ww[0]);
        int maxY = Math.max(0, mode.height() - wh[0]);

        int newX = wx[0] + (int) (bounceVx * WINDOW_SPEED_PX);
        int newY = wy[0] + (int) (bounceVy * WINDOW_SPEED_PX);
        if (newX <= 0 || newX >= maxX) {
            bounceVx = -bounceVx;
            newX = Math.max(0, Math.min(newX, maxX));
        }
        if (newY <= 0 || newY >= maxY) {
            bounceVy = -bounceVy;
            newY = Math.max(0, Math.min(newY, maxY));
        }
        GLFW.glfwSetWindowPos(handle, newX, newY);
    }
}
