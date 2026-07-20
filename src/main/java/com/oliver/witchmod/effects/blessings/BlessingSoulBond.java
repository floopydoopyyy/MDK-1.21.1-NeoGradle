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
 * You feel spiritually tethered to someone who has your back.
 *
 * <p>CLAUDE.md flags a manual cost surcharge to 100 as recommended for this blessing (section 5.2);
 * kept at the documented base 63 here since surcharges are a balancing-pass concern (Phase 6). Full
 * two-player bonding (sharing damage/status with a specific bonded partner) is deferred — this is the
 * self-buff placeholder for now.
 */
public final class BlessingSoulBond extends Effect {
    public BlessingSoulBond() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 63, () -> Items.TOTEM_OF_UNDYING);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.REGENERATION, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.REGENERATION);
    }
}
