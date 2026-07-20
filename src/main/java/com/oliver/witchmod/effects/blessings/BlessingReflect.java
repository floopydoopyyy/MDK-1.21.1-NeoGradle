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
 * Harm just doesn't land quite as hard on you right now.
 *
 * <p>Full curse-redirection (mirroring the Ward item's job) is a Table/Ledger concern for Phase 4;
 * this is the passive damage-reduction side of it, available now.
 */
public final class BlessingReflect extends Effect {
    public BlessingReflect() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.TURTLE_HELMET);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.DAMAGE_RESISTANCE, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.DAMAGE_RESISTANCE);
    }
}
