package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Please wait... loading region... this Bethesda joke never ends (master-spec Loading Screen — a
 * FULLY-FUNCTIONAL custom overlay, Phase D).
 *
 * <p>The actual trigger is event-driven, not tick-based: opening a door/trapdoor/fence gate has a chance
 * (on a cooldown) to flash a fullscreen fake loading screen — see the door hook in {@code CurseEventHandler}
 * (server) and {@code client/LoadingScreenOverlay} (render), coordinated through the auto-synced
 * {@link WitchModAttachments#LOADING_SCREEN_END_TICK}. This class just declares cost/sacrificial item and
 * makes sure a curse cure/expiry dismisses any overlay in progress.
 *
 * <p>PROTOTYPE gaps: the custom ambience sound (Section 12) and the writable {@code loading_tips.json} list
 * are deferred — the overlay uses a small hardcoded tip pool for now.
 */
public final class CurseLoadingScreen extends Effect {
    public static final int DURATION_TICKS = 60;
    public static final float CHANCE = 0.5F;
    public static final int COOLDOWN_TICKS = 400;

    public CurseLoadingScreen() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.GLISTERING_MELON_SLICE);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.LOADING_SCREEN_END_TICK, 0L); // dismiss any overlay in progress
    }
}
