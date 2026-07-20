package com.oliver.witchmod.events.globals;

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

/** Every online player gets a random curse or blessing. The house always wins something. */
public final class GlobalGamble extends BewitchmentEvent {
    public GlobalGamble() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        List<Holder.Reference<Effect>> all = WitchModRegistries.EFFECT_REGISTRY.holders().toList();
        if (all.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            Holder.Reference<Effect> effect = all.get(level.random.nextInt(all.size()));
            EffectManager.apply(player, effect, durationTicks, null);
        }
    }
}
