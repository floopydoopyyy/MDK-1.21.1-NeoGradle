package com.oliver.witchmod.effects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.effects.blessings.BlessingBlacksmith;
import com.oliver.witchmod.effects.blessings.BlessingLastStand;
import com.oliver.witchmod.effects.blessings.BlessingThickSkinned;

/** Blessing hooks that need a game event rather than onApply/onTick/onRemove. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class BlessingEventHandler {
    private BlessingEventHandler() {}

    @SubscribeEvent
    static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Immortality: save from death, breaks after this use (existing behavior).
        if (EffectManager.isActive(player, Blessings.IMMORTALITY)) {
            event.setCanceled(true);
            EffectManager.remove(player, Blessings.IMMORTALITY);
            player.setHealth(1.0F);
            return;
        }
        // Last Stand: single-use revive with brief buffs; consumes the blessing (master-spec Section 6).
        if (EffectManager.isActive(player, Blessings.LAST_STAND)) {
            event.setCanceled(true);
            EffectManager.remove(player, Blessings.LAST_STAND);
            player.setHealth(BlessingLastStand.REVIVE_HEALTH);
            EffectUtil.addTimedEffect(player, MobEffects.DAMAGE_BOOST, BlessingLastStand.BUFF_TICKS, 1);
            EffectUtil.addTimedEffect(player, MobEffects.MOVEMENT_SPEED, BlessingLastStand.BUFF_TICKS, 1);
            EffectUtil.addTimedEffect(player, MobEffects.DAMAGE_RESISTANCE, BlessingLastStand.BUFF_TICKS, 1);
        }
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.THICK_SKINNED)
                && event.getAmount() <= BlessingThickSkinned.DAMAGE_FLOOR) {
            // Small tick damage ignored — single events at or below the floor are fully negated.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.BOUNCY)) {
            // Fall damage negated (the real bounce physics/sounds are deferred — see BlessingBouncy).
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && !event.getOutput().isEmpty()
                && EffectManager.isActive(player, Blessings.BLACKSMITH)) {
            long reduced = Math.max(1L, (long) (event.getCost() * BlessingBlacksmith.ANVIL_COST_MULT));
            event.setCost(reduced);
        }
    }

    @SubscribeEvent
    static void onServerChat(ServerChatEvent event) {
        ServerPlayer speaker = event.getPlayer();
        if (!EffectManager.isActive(speaker, Blessings.LAUGH_TRACK)) {
            return;
        }
        // PROTOTYPE: a server-wide laugh on every message from the blessed player. The custom laugh-track
        // OGGs (Section 12) and the per-message cooldown are deferred — a vanilla sound stands in for now.
        for (Player online : speaker.serverLevel().players()) {
            if (online instanceof ServerPlayer listener) {
                listener.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }
}
