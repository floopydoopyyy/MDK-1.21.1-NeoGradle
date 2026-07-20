package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Forward isn't where you thought it was. Smooth criminal (master-spec Moonwalker, a client-side curse,
 * Phase D). Drives the auto-synced {@link WitchModAttachments#MOONWALKER_ACTIVE} flag; the client reverses
 * the forward/back movement input while it's set (see {@code client/ClientCurseHandler}). The spec's
 * shorter duration override (6000-12000t) is a caster/table concern, not enforced from inside the effect.
 */
public final class CurseMoonwalker extends Effect {
    public CurseMoonwalker() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.END_STONE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.MOONWALKER_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.MOONWALKER_ACTIVE, -1);
    }
}
