package com.oliver.witchmod.events.neutrals;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EventCategory;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * Backfire-only special case (CLAUDE.md section 2.1/4.3/4.5) — no sacrificial item, no essence cost,
 * system-triggered when a Table cast fails. The Table itself doesn't exist until Phase 4, so this is a
 * self-contained stand-in for now: picks a random curse and lands it on the initiator, the same
 * consequence a real backfire would have. Exposed under {@code /bewitch event neutral} purely for
 * testing that consequence ahead of the real Table wiring.
 */
public final class NeutralMirror extends BewitchmentEvent {
    public NeutralMirror() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        List<Holder.Reference<Effect>> curses = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().category() == EffectCategory.CURSE)
                .toList();
        if (curses.isEmpty()) {
            return;
        }
        Holder.Reference<Effect> curse = curses.get(initiator.getRandom().nextInt(curses.size()));
        EffectManager.apply(initiator, curse, durationTicks, null);
    }
}
