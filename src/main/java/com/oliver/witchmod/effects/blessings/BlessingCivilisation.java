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
 * Sure-footed and forgiving. Ledges cut you some slack.
 *
 * <p>PROTOTYPE: the full spec gives parkour "coyote time" (a few ticks to still jump after leaving an
 * edge) plus a slight edge-magnetism. Both are movement-system tweaks. As a loosely-functional stand-in
 * this grants Jump Boost while active; the coyote-time and edge bias are deferred.
 */
public final class BlessingCivilisation extends Effect {
    public BlessingCivilisation() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.BEEF);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.JUMP, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.JUMP);
    }
}
