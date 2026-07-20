package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Every big moment gets a dramatic beat (master-spec Pacing, a client-side curse, Phase D). Landing a hit
 * has a chance (on a cooldown) to freeze the victim briefly and hijack the camera for a cinematic moment —
 * see the trigger in {@code CurseEventHandler} (server) and the camera roll in {@code client/ClientCurseHandler}
 * (client), coordinated through the auto-synced {@link WitchModAttachments#PACING_END_TICK}.
 *
 * <p>PROTOTYPE: the full spec cuts the camera between nearby entities across several shots and freezes the
 * involved enemy too; the stand-in here is a brief self-freeze (Slowness applied at trigger time) plus a
 * dramatic camera roll. Custom sting sound (Section 12) deferred. This class just declares cost/item.
 */
public final class CursePacing extends Effect {
    public static final float TRIGGER_CHANCE = 0.10F;
    public static final int COOLDOWN_TICKS = 1200;
    public static final int FREEZE_TICKS = 80;

    public CursePacing() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.TROPICAL_FISH);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.PACING_END_TICK, 0L); // dismiss any dramatic moment in progress
    }
}
