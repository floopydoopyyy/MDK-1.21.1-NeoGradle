package com.oliver.witchmod.data;

import java.util.OptionalInt;

/**
 * The 18 Table modifiers (CLAUDE.md section 4.6/5.6). Pure data — no item coupling here; the Table
 * (Phase 4) is responsible for mapping a physical item in its Modifier slot to one of these constants.
 * All percentages are deltas applied on top of the Section 5.7 base curves; see {@link ModifierCalculator}
 * for how they combine.
 */
public enum Modifier {
    CLOCK(15, 25, 0, 0),
    COMPASS(5, 0, 0, 0) {
        @Override
        public OptionalInt fixedDurationTicks() {
            return OptionalInt.of(40 * 60 * 20);
        }
    },
    NETHERSTAR(50, 0, 0, 0) {
        @Override
        public OptionalInt successCapPercent() {
            return OptionalInt.of(99);
        }
    },
    PRISMARINE_SHARD(-15, 0, 0, 0),
    QUARTZ(-20, 0, 0, 0) {
        @Override
        public boolean discoveredEffectsOnly() {
            return true;
        }
    },
    DRAGONS_BREATH(30, -40, 0, 10) {
        @Override
        public boolean splashToNearby() {
            return true;
        }
    },
    NETHERITE_INGOT(40, 0, 0, 10) {
        @Override
        public boolean bypassesWardAndJar() {
            return true;
        }
    },
    INK_SAC(35, 0, 0, 5) {
        @Override
        public boolean hidesStartTell() {
            return true;
        }
    },
    GLOW_INK_SAC(-10, 0, 0, 0) {
        @Override
        public boolean revealsEffectToTarget() {
            return true;
        }
    },
    RABBITS_FOOT(10, 0, 0, -15),
    ECHO_SHARD(10, 0, 0, 0) {
        @Override
        public boolean delaysTell() {
            return true;
        }
    },
    GOAT_HORN(-5, 0, 0, 0) {
        @Override
        public boolean loudTriggerTell() {
            return true;
        }
    },
    SUGAR(-10, -50, 0, 0),
    HONEYCOMB(20, -60, 0, 0) {
        @Override
        public boolean forcesBackfireZero() {
            return true;
        }
    },
    // Gunpowder, Redstone Dust, and Milk Bucket were removed as modifiers per master-spec Section 9 — they
    // are now sacrificial items / table mechanics instead (Gunpowder → Explosive curse, Milk Bucket →
    // Butterfingers curse, Redstone Dust → the random-attachment table mechanic in BewitchingTableRitual).
    RECOVERY_COMPASS(15, 0, 0, 0) {
        @Override
        public boolean reappliesShortenedEffectOnCure() {
            return true;
        }
    };

    private final int costDeltaPercent;
    private final int durationDeltaPercent;
    private final int successDeltaPercent;
    private final int backfireDeltaPercent;

    Modifier(int costDeltaPercent, int durationDeltaPercent, int successDeltaPercent, int backfireDeltaPercent) {
        this.costDeltaPercent = costDeltaPercent;
        this.durationDeltaPercent = durationDeltaPercent;
        this.successDeltaPercent = successDeltaPercent;
        this.backfireDeltaPercent = backfireDeltaPercent;
    }

    public int costDeltaPercent() {
        return costDeltaPercent;
    }

    public int durationDeltaPercent() {
        return durationDeltaPercent;
    }

    public int successDeltaPercent() {
        return successDeltaPercent;
    }

    public int backfireDeltaPercent() {
        return backfireDeltaPercent;
    }

    /** Compass only: overrides duration to a fixed 40 minutes instead of applying a % delta. */
    public OptionalInt fixedDurationTicks() {
        return OptionalInt.empty();
    }

    /** Netherstar only: raises the normal 95% success cap (section 5.7) to this value instead. */
    public OptionalInt successCapPercent() {
        return OptionalInt.empty();
    }

    /** Quartz only: usable exclusively on effects the caster has already discovered. */
    public boolean discoveredEffectsOnly() {
        return false;
    }

    /** Dragon's Breath only: also splashes a shortened dose onto players near the target. */
    public boolean splashToNearby() {
        return false;
    }

    /** Netherite Ingot only: ignores Ward/Jar counterplay (Totem still applies). */
    public boolean bypassesWardAndJar() {
        return false;
    }

    /** Ink Sac only: the target gets no "something happened" tell at all when this lands. */
    public boolean hidesStartTell() {
        return false;
    }

    /** Glow Ink Sac only: the target is told exactly which effect landed, not just that one did. */
    public boolean revealsEffectToTarget() {
        return false;
    }

    /** Echo Shard only: delays the target's tell notification, not the effect's actual onset. */
    public boolean delaysTell() {
        return false;
    }

    /** Goat Horn only: the tell is loud/obvious instead of the normal subtle one. */
    public boolean loudTriggerTell() {
        return false;
    }

    /** Honeycomb only: backfire chance is forced to 0% regardless of the base curve. */
    public boolean forcesBackfireZero() {
        return false;
    }

    /** Recovery Compass only: on cure, reapplies the effect once more at a shortened duration. */
    public boolean reappliesShortenedEffectOnCure() {
        return false;
    }
}
