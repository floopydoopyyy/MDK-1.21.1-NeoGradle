package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Your whole game window drifts off like a DVD screensaver (master-spec Screensaver, a client-side curse,
 * Phase D). Drives the auto-synced {@link WitchModAttachments#SCREENSAVER_ACTIVE} flag; the client bounces
 * the OS window around the monitor in occasional episodes while it's set (see {@code client/ClientCurseHandler}).
 * No-op if the window can't be moved (e.g. fullscreen).
 */
public final class CurseScreensaver extends Effect {
    public CurseScreensaver() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.PAINTING);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, -1);
    }
}
