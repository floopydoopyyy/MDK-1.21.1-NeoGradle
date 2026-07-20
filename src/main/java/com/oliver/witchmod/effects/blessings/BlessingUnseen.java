package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * Slip out of sight. People only notice you when you're right on top of them.
 *
 * <p>PROTOTYPE: the full spec hides the player (armour/held included) from clients beyond a proximity
 * radius, fading in rather than hard-popping. As a loosely-functional stand-in this applies vanilla
 * Invisibility (which notably does NOT hide armour/held items) for the duration. The proximity-based reveal
 * and full-render hiding are deferred.
 */
public final class BlessingUnseen extends Effect {
    public BlessingUnseen() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.INK_SAC);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.INVISIBILITY, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.INVISIBILITY);
    }
}
