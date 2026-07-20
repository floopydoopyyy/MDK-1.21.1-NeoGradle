package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The Loading Screen curse's fake fullscreen overlay (Phase D / Section 13.4): a black screen with a
 * spinner and a useless tip. Renders while the (synced) world game time is below the player's
 * {@link WitchModAttachments#LOADING_SCREEN_END_TICK}. Registered above all other HUD layers so it covers
 * the whole screen.
 *
 * <p>PROTOTYPE: tips are a small hardcoded pool (the writable {@code loading_tips.json} is deferred), and
 * the spinner is drawn from text frames since no spinner texture exists yet.
 */
public final class LoadingScreenOverlay implements LayeredDraw.Layer {
    private static final String[] TIPS = {
            "Tip: You can open doors by right-clicking them.",
            "Tip: Water is wet. Plan accordingly.",
            "Tip: Creepers dislike being hugged.",
            "Tip: If you are reading this, the game is still loading.",
            "Tip: Press any key to continue. (This does nothing.)",
            "Tip: Sleeping skips the night. Doors do not.",
            "Loading assets you already had...",
    };
    private static final String[] SPINNER = {"|", "/", "-", "\\"};

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long end = player.getData(WitchModAttachments.LOADING_SCREEN_END_TICK);
        long now = mc.level.getGameTime();
        if (now >= end) {
            return; // not currently loading
        }

        int w = guiGraphics.guiWidth();
        int h = guiGraphics.guiHeight();
        Font font = mc.font;

        guiGraphics.fill(0, 0, w, h, 0xFF000000);

        String tip = TIPS[(int) Mth.positiveModulo(end, TIPS.length)]; // stable for the whole flash (end is fixed)
        String spinner = SPINNER[(int) Mth.positiveModulo(now / 2, SPINNER.length)];

        guiGraphics.drawCenteredString(font, Component.literal("Loading..."), w / 2, h / 2 - 20, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, Component.literal(spinner), w / 2, h / 2, 0xFFAAAAAA);
        guiGraphics.drawCenteredString(font, Component.literal(tip), w / 2, h - 40, 0xFF888888);
    }
}
