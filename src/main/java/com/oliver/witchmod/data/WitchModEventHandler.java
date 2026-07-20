package com.oliver.witchmod.data;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.oliver.witchmod.WitchMod;

@EventBusSubscriber(modid = WitchMod.MODID)
public final class WitchModEventHandler {
    private WitchModEventHandler() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffectManager.tick(player);
            AfflictionManager.tick(player);
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GracePeriod.markFirstSeenIfAbsent(player);
        }
    }
}
