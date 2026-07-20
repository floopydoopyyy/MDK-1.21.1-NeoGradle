package com.oliver.witchmod.data;

import org.jetbrains.annotations.Nullable;

import com.oliver.witchmod.Config;

/**
 * The Section 5.7 success/backfire formulas plus modifier deltas, as standalone functions so the
 * Bewitching Table (Phase 4) can consume them directly without any Table-specific plumbing. The formula's
 * own constants (floor/span/cap/backfire-start) are read from {@link Config} live rather than hardcoded
 * (Phase 6), so a server admin can rebalance the whole curve without a code change.
 */
public final class ModifierCalculator {
    private ModifierCalculator() {}

    public static int applyCost(int baseCost, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseCost;
        }
        return Math.round(baseCost * (1 + modifier.costDeltaPercent() / 100F));
    }

    public static int applyDuration(int baseDurationTicks, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseDurationTicks;
        }
        return modifier.fixedDurationTicks().orElseGet(
                () -> Math.round(baseDurationTicks * (1 + modifier.durationDeltaPercent() / 100F)));
    }

    /** {@code successChance = min(cap, floor + span * (essenceSpent / baseCost))}, per section 5.7. */
    public static float baseSuccessChance(int essenceSpent, int baseCost) {
        float ratio = baseCost <= 0 ? 1F : (float) essenceSpent / baseCost;
        float floor = Config.SUCCESS_FLOOR_PERCENT.get() / 100F;
        float span = Config.SUCCESS_SPAN_PERCENT.get() / 100F;
        return Math.min(successCap(), floor + span * ratio);
    }

    public static float applySuccessChance(float baseSuccessChance, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseSuccessChance;
        }
        float withDelta = baseSuccessChance + modifier.successDeltaPercent() / 100F;
        float cap = modifier.successCapPercent().isPresent() ? modifier.successCapPercent().getAsInt() / 100F : successCap();
        return Math.min(cap, withDelta);
    }

    /** {@code backfireChance = max(0%, start - (essenceSpent / baseCost) * start)}, per section 5.7. */
    public static float baseBackfireChance(int essenceSpent, int baseCost) {
        float ratio = baseCost <= 0 ? 1F : (float) essenceSpent / baseCost;
        float start = Config.BACKFIRE_START_PERCENT.get() / 100F;
        return Math.max(0F, start - ratio * start);
    }

    private static float successCap() {
        return Config.SUCCESS_CAP_PERCENT.get() / 100F;
    }

    public static float applyBackfireChance(float baseBackfireChance, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseBackfireChance;
        }
        if (modifier.forcesBackfireZero()) {
            return 0F;
        }
        return Math.max(0F, baseBackfireChance + modifier.backfireDeltaPercent() / 100F);
    }
}
