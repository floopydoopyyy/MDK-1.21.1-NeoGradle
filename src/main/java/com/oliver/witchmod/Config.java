package com.oliver.witchmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Real server config (CLAUDE.md section 7/10 Phase 6), replacing the MDK's example scaffold. Every
 * gamerule named in section 7 lives here, plus the section 5.7 success/backfire formula constants, plus a
 * generic "id=value" override list for the ~70 individual per-effect essence costs from sections 5.1/5.2 —
 * a full one-field-per-effect config would be ~150 static fields for numbers this document itself calls
 * "brief functional prototype... treat none of the current implementations as final design intent"
 * (section 4), so the override-list format keeps every cost genuinely tunable without code changes while
 * staying proportionate to how provisional those numbers are.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CURSES_ENABLED = BUILDER
            .comment("Whether curses can be applied at all.")
            .define("cursesEnabled", true);

    public static final ModConfigSpec.BooleanValue BLESSINGS_ENABLED = BUILDER
            .comment("Whether blessings can be applied at all.")
            .define("blessingsEnabled", true);

    public static final ModConfigSpec.BooleanValue GLOBALS_ENABLED = BUILDER
            .comment("Whether Global events (server-wide, bank-funded) can be triggered.")
            .define("globalsEnabled", true);

    public static final ModConfigSpec.BooleanValue BACKFIRES_ENABLED = BUILDER
            .comment("Whether a failed Table ritual can backfire onto the caster. When false, that",
                    "probability mass falls through to a Neutral miss instead.")
            .define("backfiresEnabled", true);

    public static final ModConfigSpec.BooleanValue WARD_DURABILITY_DECAYS = BUILDER
            .comment("Whether deflecting a curse costs the Ward a durability point. Set false to let",
                    "players opt out of the durability tax entirely.")
            .define("wardDurabilityDecays", true);

    public static final ModConfigSpec.IntValue GRACE_PERIOD_TICKS = BUILDER
            .comment("How many ticks after a player's first-ever join they cannot be targeted by another",
                    "player's curse/blessing (self-casts are unaffected). 6000 ticks = 5 minutes. 0 disables it.")
            .defineInRange("gracePeriodTicks", 6000, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_EFFECTS_PER_PLAYER = BUILDER
            .comment("Max simultaneous curses+blessings a player can carry before further casts risk",
                    "backfiring instead of landing cleanly (CLAUDE.md section 2.7).")
            .defineInRange("maxActiveEffectsPerPlayer", 3, 1, 64);

    public static final ModConfigSpec.IntValue LIMIT_BACKFIRE_BOOST_PERCENT = BUILDER
            .comment("Flat backfire-chance increase (percentage points) applied when the target is already",
                    "at or over maxActiveEffectsPerPlayer. A Phase 6 balancing knob, not a value CLAUDE.md pins down.")
            .defineInRange("limitBackfireBoostPercent", 20, 0, 100);

    public static final ModConfigSpec.IntValue ESCALATING_COST_PERCENT = BUILDER
            .comment("Extra essence cost (percent) required to cast an effect on a target who already has",
                    "that exact effect active (CLAUDE.md section 2.7's \"escalating cost\").")
            .defineInRange("escalatingCostPercent", 50, 0, 500);

    public static final ModConfigSpec.IntValue SUCCESS_FLOOR_PERCENT = BUILDER
            .comment("Section 5.7 formula: minimum success chance regardless of essence spent.")
            .defineInRange("successFloorPercent", 30, 0, 100);

    public static final ModConfigSpec.IntValue SUCCESS_SPAN_PERCENT = BUILDER
            .comment("Section 5.7 formula: how much essenceSpent/baseCost can add on top of the floor.")
            .defineInRange("successSpanPercent", 65, 0, 100);

    public static final ModConfigSpec.IntValue SUCCESS_CAP_PERCENT = BUILDER
            .comment("Section 5.7 formula: success chance never exceeds this (Netherstar modifier overrides it).")
            .defineInRange("successCapPercent", 95, 0, 100);

    public static final ModConfigSpec.IntValue BACKFIRE_START_PERCENT = BUILDER
            .comment("Section 5.7 formula: backfire chance at essenceSpent = 0, scaling down to 0 at full cost.")
            .defineInRange("backfireStartPercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue GLOBAL_BASE_CHANCE_PERCENT = BUILDER
            .comment("Globals share ONE server-wide cooldown (master-spec Section 8). This is the base ceiling",
                    "the shared success chance climbs toward — \"very low base odds\". The chance a player's",
                    "global attempt succeeds is this * (how far the shared charge has recharged).")
            .defineInRange("globalBaseChancePercent", 15, 0, 100);

    public static final ModConfigSpec.IntValue GLOBAL_RECHARGE_HOURS = BUILDER
            .comment("Hours of world runtime for the shared global success chance to climb from 0 back up to",
                    "globalBaseChancePercent after a global fires. 0 = always at the base chance (no cooldown).")
            .defineInRange("globalRechargeHours", 3, 0, 168);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EFFECT_COST_OVERRIDES = BUILDER
            .comment("Per-effect essence cost overrides, format \"witchmod:effect_id=cost\". Any effect not",
                    "listed here keeps its code-defined default cost (section 5.1/5.2).")
            .defineListAllowEmpty("effectCostOverrides", List.of(), () -> "", Config::validateOverrideEntry);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateOverrideEntry(Object obj) {
        if (!(obj instanceof String entry)) {
            return false;
        }
        int split = entry.indexOf('=');
        if (split < 0) {
            return false;
        }
        try {
            Integer.parseInt(entry.substring(split + 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Parses {@link #EFFECT_COST_OVERRIDES} into an id-&gt;cost lookup. Cheap enough to call per-cast; not cached since config can reload. */
    public static Map<String, Integer> parsedEffectCostOverrides() {
        Map<String, Integer> parsed = new HashMap<>();
        for (String entry : EFFECT_COST_OVERRIDES.get()) {
            int split = entry.indexOf('=');
            if (split < 0) {
                continue;
            }
            try {
                parsed.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            } catch (NumberFormatException ignored) {
                // Already validated on load; defensive only.
            }
        }
        return parsed;
    }

    private Config() {}
}
