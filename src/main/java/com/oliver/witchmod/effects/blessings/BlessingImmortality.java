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
 * Death just doesn't stick to you right now — see {@code BlessingEventHandler}'s
 * {@code LivingDeathEvent} listener for the actual save.
 *
 * <p>CLAUDE.md flags a manual cost surcharge to 100 as recommended for this blessing (section 5.2);
 * kept at the documented base 63 here since surcharges are a balancing-pass concern (Phase 6).
 */
public final class BlessingImmortality extends Effect {
    public BlessingImmortality() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 63, () -> Items.GHAST_TEAR);
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
