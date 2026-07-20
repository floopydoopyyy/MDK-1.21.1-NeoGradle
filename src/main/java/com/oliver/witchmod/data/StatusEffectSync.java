package com.oliver.witchmod.data;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Keeps the three wrapper status effects (CLAUDE.md section 2.6) matched to whatever's actually active.
 * Call {@link #sync} after any mutation to a player's active curses/blessings/afflictions —
 * {@link EffectManager} and {@link AfflictionManager} already do this for every mutation they make.
 */
public final class StatusEffectSync {
    private static final int PARTICLE_COUNT = 20;

    private StatusEffectSync() {}

    public static void sync(ServerPlayer player) {
        updateWrapper(player, WitchModMobEffects.CURSED, maxRemaining(player, EffectCategory.CURSE));
        updateWrapper(player, WitchModMobEffects.BLESSED, maxRemaining(player, EffectCategory.BLESSING));
        updateWrapper(player, WitchModMobEffects.AFFLICTED, maxAfflictionRemaining(player));
    }

    private static int maxRemaining(ServerPlayer player, EffectCategory category) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null) {
            return 0;
        }
        int max = 0;
        for (ResourceLocation id : active.activeIds()) {
            boolean matches = WitchModRegistries.EFFECT_REGISTRY.getOptional(id)
                    .map(effect -> effect.category() == category)
                    .orElse(false);
            if (matches) {
                max = Math.max(max, active.get(id).map(ActiveEffectInstance::remainingTicks).orElse(0));
            }
        }
        return max;
    }

    private static int maxAfflictionRemaining(ServerPlayer player) {
        ActiveAfflictions active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_AFFLICTIONS);
        return active == null ? 0 : active.maxRemaining();
    }

    /**
     * Matches one wrapper's duration to {@code duration} (0 = remove). Bursts particles ONLY on the
     * absent→present onset and the present→absent expiry (master-spec Rule 6 — "never in between"); the
     * wrapper itself is applied with particles OFF (so vanilla doesn't render its swirl every tick) but its
     * icon ON (the victim knows they're Cursed/Blessed/Afflicted, just not the specifics). Re-added fresh
     * each sync so the duration tracks exactly, including SHORTENING (e.g. Purifying Water burn-down) —
     * vanilla's own {@code addEffect} keep-longer merge would otherwise refuse to shrink it.
     */
    private static void updateWrapper(ServerPlayer player, Holder<MobEffect> wrapper, int duration) {
        boolean had = player.hasEffect(wrapper);
        if (duration > 0) {
            if (had) {
                player.removeEffect(wrapper);
            }
            player.addEffect(new MobEffectInstance(wrapper, duration, 0, false, false, true));
            if (!had) {
                burstParticles(player);
            }
        } else if (had) {
            player.removeEffect(wrapper);
            burstParticles(player);
        }
    }

    private static void burstParticles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), PARTICLE_COUNT, 0.3, 0.5, 0.3, 0.05);
    }
}
