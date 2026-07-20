package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.curses.CurseThirstMeter;

/**
 * Renders the Thirst Meter bar (Phase D / Section 13.4) as a row of droplets above the hunger bar, driven
 * by the auto-synced {@link WitchModAttachments#THIRST} attribute ({@code -1} = curse inactive → hidden).
 *
 * <p>No {@code thirst_icons.png} exists yet (Human Action Items), so droplets are drawn as simple filled
 * rectangles — a drop-in {@code blit} swap later. The VALUE and behaviour are fully functional; only the
 * art is placeholder.
 */
public final class ThirstHudLayer implements LayeredDraw.Layer {
    private static final int ICONS = 10;                 // 10 droplets, 2 thirst points each
    private static final int ICON_SPACING = 8;
    private static final int ICON_SIZE = 7;
    private static final int SLOT_COLOR = 0xFF101B2E;     // empty droplet
    private static final int WATER_COLOR = 0xFF3AA0FF;    // filled droplet
    private static final int OUTLINE_COLOR = 0xFF0A1220;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // Hidden in creative/spectator, matching vanilla's survival-HUD gate (hunger, health, air).
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        LocalPlayer player = mc.player;
        int thirst = player.getData(WitchModAttachments.THIRST);
        if (thirst < 0) {
            return; // Thirst Meter curse not active
        }

        int right = guiGraphics.guiWidth() / 2 + 91;      // vanilla food-bar right edge
        int top = HudBars.topForRow(guiGraphics.guiHeight(), HudBars.thirstRow(player)); // above Gluttony if it's active too

        for (int i = 0; i < ICONS; i++) {
            int x = right - i * ICON_SPACING - 9;
            int filled = Math.max(0, Math.min(2, thirst - i * 2)); // 0 empty, 1 half, 2 full

            // empty droplet slot
            guiGraphics.fill(x - 1, top - 1, x + ICON_SIZE + 1, top + ICON_SIZE + 1, OUTLINE_COLOR);
            guiGraphics.fill(x, top, x + ICON_SIZE, top + ICON_SIZE, SLOT_COLOR);
            if (filled == 2) {
                guiGraphics.fill(x, top, x + ICON_SIZE, top + ICON_SIZE, WATER_COLOR);
            } else if (filled == 1) {
                guiGraphics.fill(x, top, x + ICON_SIZE / 2 + 1, top + ICON_SIZE, WATER_COLOR);
            }
        }
    }

    /** Sanity reference so this layer and the curse stay in step on the max value. */
    static int maxThirst() {
        return CurseThirstMeter.THIRST_MAX;
    }
}
