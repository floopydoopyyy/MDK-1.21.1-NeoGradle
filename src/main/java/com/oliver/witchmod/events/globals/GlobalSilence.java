package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.EventCategory;

/**
 * Everything goes quiet, server-wide.
 *
 * <p>Real chat-muting needs a persistent, server-wide flag plus a chat-event hook — deliberately out of
 * scope for this lower-priority global (CLAUDE.md section 10, Phase 1). This is the sensory side of it
 * (Darkness for everyone) as a stand-in.
 */
public final class GlobalSilence extends BewitchmentEvent {
    public GlobalSilence() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.addTimedEffect(player, MobEffects.DARKNESS, durationTicks, 0);
            player.sendSystemMessage(Component.literal("Everything just went quiet."));
        }
    }

    @Override
    public void stop(ServerLevel level, @Nullable ServerPlayer target) {
        for (ServerPlayer player : level.players()) {
            EffectUtil.removeTimedEffect(player, MobEffects.DARKNESS);
        }
    }
}
