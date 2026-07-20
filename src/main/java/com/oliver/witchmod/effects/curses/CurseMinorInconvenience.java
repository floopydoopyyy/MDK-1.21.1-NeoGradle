package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Nothing serious — your game window just keeps getting renamed to nonsense (master-spec Minor
 * Inconvenience, a client-side curse, Phase D). Drives the auto-synced
 * {@link WitchModAttachments#MINOR_INCONVENIENCE_ACTIVE} flag; the client renames the OS window title to
 * silly strings on an interval while it's set (see {@code client/ClientCurseHandler}).
 *
 * <p>PROTOTYPE: the spec also blocks fullscreen and pulls titles from a writable {@code window_titles.json};
 * both are deferred (a hardcoded title pool is used, fullscreen-block not yet wired).
 */
public final class CurseMinorInconvenience extends Effect {
    public CurseMinorInconvenience() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.COBWEB);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.MINOR_INCONVENIENCE_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.MINOR_INCONVENIENCE_ACTIVE, -1);
    }
}
