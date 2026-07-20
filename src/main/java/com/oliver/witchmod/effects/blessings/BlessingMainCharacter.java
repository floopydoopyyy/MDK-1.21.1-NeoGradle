package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * Everything about right now feels a little more cinematic.
 *
 * <p>Sacrificial item is Fire Charge, resolving the Section 11 HARD collision (Main Character vs the
 * Celebration neutral, both otherwise on a firework): Celebration is inherently about fireworks so it keeps
 * the Firework Star, while the action-protagonist Main Character takes the punchier Fire Charge (per
 * Oliver's call). This also drops the awkward "match any Firework Star regardless of components" type-match,
 * since Fire Charge has no colour components.
 */
public final class BlessingMainCharacter extends Effect {
    private static final int INTERVAL_TICKS = 300;

    public BlessingMainCharacter() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.FIRE_CHARGE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.GLOWING, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.GLOWING);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.sendParticles(ParticleTypes.FIREWORK, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.5, 0.5, 0.5, 0.05);
        }
    }
}
