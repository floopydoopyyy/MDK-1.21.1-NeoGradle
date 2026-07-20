package com.oliver.witchmod.data;

/**
 * Matches the Minor/Moderate/Major weight labels used throughout CLAUDE.md section 5.
 * Purely descriptive — the actual numeric cost lives on the {@link Effect} itself and is what the
 * success/backfire formulas (section 5.7) consume.
 */
public enum EffectCostTier {
    MINOR,
    MODERATE,
    MAJOR
}
