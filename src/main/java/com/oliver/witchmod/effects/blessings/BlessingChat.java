package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Your own personal Twitch chat, hyping you up (Phase D CUSTOM UI). Drives the auto-synced
 * {@link WitchModAttachments#CHAT_OVERLAY} flag; the actual scrolling chat panel is rendered client-side by
 * {@code client/ChatOverlayLayer}, which generates lines locally while the blessing is active.
 *
 * <p>PROTOTYPE: lines come from a hardcoded pool (the writable {@code twitch_chat.json} is deferred), and
 * the "surface real useful info" behaviour (nearby structures/chests, low durability) isn't wired yet.
 */
public final class BlessingChat extends Effect {
    public BlessingChat() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.PURPLE_WOOL);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.CHAT_OVERLAY, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.CHAT_OVERLAY, -1);
    }
}
