package com.oliver.witchmod.client;

import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Shared layout for WitchMod's right-side stacked HUD bars (Phase D) so multiple can be active at once
 * without overlapping. Bars stack upward from just above the vanilla hunger bar; each bar's row is the
 * count of active bars sitting below it. Order (bottom→top, nearest hunger first): Gluttony (a second
 * hunger row) then Thirst. Add future right-side bars to this ordering in one place.
 */
final class HudBars {
    /** Row 0's top edge is this many pixels up from the bottom of the screen (hunger sits at 39). */
    private static final int ROW0_FROM_BOTTOM = 49;
    private static final int ROW_HEIGHT = 10;

    private HudBars() {}

    static boolean gluttonyActive(LocalPlayer player) {
        return player.getData(WitchModAttachments.GLUTTONY_HUNGER) >= 0;
    }

    static boolean thirstActive(LocalPlayer player) {
        return player.getData(WitchModAttachments.THIRST) >= 0;
    }

    static int topForRow(int guiHeight, int row) {
        return guiHeight - ROW0_FROM_BOTTOM - row * ROW_HEIGHT;
    }

    /** Gluttony is the bottom bar (it IS a hunger row), so always row 0 when shown. */
    static int gluttonyRow(LocalPlayer player) {
        return 0;
    }

    /** Thirst sits above Gluttony when both are active. */
    static int thirstRow(LocalPlayer player) {
        return gluttonyActive(player) ? 1 : 0;
    }
}
