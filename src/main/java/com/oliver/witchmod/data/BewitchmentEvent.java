package com.oliver.witchmod.data;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A single neutral or global event (CLAUDE.md section 2.7/4.3/4.4), hooked in here for Phase 1 to
 * implement per event. Registered through {@link WitchModRegistries#EVENTS}.
 */
public abstract class BewitchmentEvent {
    private final EventCategory category;

    protected BewitchmentEvent(EventCategory category) {
        this.category = category;
    }

    public EventCategory category() {
        return category;
    }

    /**
     * Begins this event. {@code initiator} is the caster for neutrals (default self), or the
     * attributed player for globals if one was given (Ledger attribution only — globals need no
     * selector); may be null.
     *
     * @param durationTicks the tracked duration for time-based events (a temporary vanilla status
     *                      effect, a hazard that lingers); instant/one-shot events can ignore this.
     */
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {}

    /**
     * Stops this event, if it's stoppable. Some events are intentionally unstoppable
     * (CLAUDE.md section 8, {@code /bewitch forcestop}) — those overrides should silently no-op.
     *
     * @param target the player to stop it for (neutrals, which target a specific player); null for
     *               globals, which need no selector.
     */
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {}
}
