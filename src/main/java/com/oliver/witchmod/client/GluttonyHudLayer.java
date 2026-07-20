package com.oliver.witchmod.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Renders Gluttony's second hunger row (Phase D / Section 13.4), driven by the auto-synced
 * {@link WitchModAttachments#GLUTTONY_HUNGER} attribute ({@code -1} = curse inactive → hidden). Stacks via
 * {@link HudBars} so it never collides with the Thirst bar.
 *
 * <p>Uses the actual vanilla hunger sprites ({@code hud/food_empty|half|full}) via the same blit logic as
 * {@code Gui#renderFood}, so it's literally a second hunger bar visually.
 */
public final class GluttonyHudLayer implements LayeredDraw.Layer {
    private static final int ICONS = 10;             // 10 drumsticks, 2 hunger points each
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // Hidden in creative/spectator, matching vanilla's survival-HUD gate (it's a hunger bar).
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        LocalPlayer player = mc.player;
        int value = player.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (value < 0) {
            return; // Gluttony curse not active
        }

        int right = guiGraphics.guiWidth() / 2 + 91;     // vanilla food-bar right edge
        int top = HudBars.topForRow(guiGraphics.guiHeight(), HudBars.gluttonyRow(player));

        RenderSystem.enableBlend();
        for (int j = 0; j < ICONS; j++) {
            int x = right - j * 8 - 9;
            guiGraphics.blitSprite(FOOD_EMPTY, x, top, 9, 9);
            if (j * 2 + 1 < value) {
                guiGraphics.blitSprite(FOOD_FULL, x, top, 9, 9);
            } else if (j * 2 + 1 == value) {
                guiGraphics.blitSprite(FOOD_HALF, x, top, 9, 9);
            }
        }
        RenderSystem.disableBlend();
    }
}
