package com.oliver.witchmod.data;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Applies, removes, and ticks down Global-event afflictions on players. Parallels {@link EffectManager}. */
public final class AfflictionManager {
    private AfflictionManager() {}

    /** Marks {@code player} as afflicted by {@code event} for {@code durationTicks}, refreshing it if already active. */
    public static void afflict(ServerPlayer player, Holder<BewitchmentEvent> event, int durationTicks) {
        ResourceLocation id = idOf(event);
        ActiveAfflictions active = player.getData(WitchModAttachments.ACTIVE_AFFLICTIONS);
        active.put(id, durationTicks);
        player.setData(WitchModAttachments.ACTIVE_AFFLICTIONS, active);
        StatusEffectSync.sync(player);
    }

    public static void clear(ServerPlayer player, Holder<BewitchmentEvent> event) {
        ResourceLocation id = idOf(event);
        ActiveAfflictions active = player.getData(WitchModAttachments.ACTIVE_AFFLICTIONS);
        active.put(id, 0);
        active.tickDown();
        player.setData(WitchModAttachments.ACTIVE_AFFLICTIONS, active);
        StatusEffectSync.sync(player);
    }

    /** Called once per player per tick by {@link WitchModEventHandler}. */
    public static void tick(ServerPlayer player) {
        ActiveAfflictions active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_AFFLICTIONS);
        if (active == null || active.isEmpty()) {
            return;
        }
        List<ResourceLocation> expired = active.tickDown();
        if (!expired.isEmpty()) {
            player.setData(WitchModAttachments.ACTIVE_AFFLICTIONS, active);
            StatusEffectSync.sync(player);
        }
    }

    private static ResourceLocation idOf(Holder<BewitchmentEvent> event) {
        return event.unwrapKey().orElseThrow().location();
    }
}
