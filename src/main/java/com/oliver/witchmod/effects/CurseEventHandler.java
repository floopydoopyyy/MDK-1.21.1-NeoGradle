package com.oliver.witchmod.effects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.curses.CurseGluttony;
import com.oliver.witchmod.effects.curses.CurseLoadingScreen;
import com.oliver.witchmod.effects.curses.CursePacing;
import com.oliver.witchmod.effects.curses.CurseSuperExplosive;
import com.oliver.witchmod.effects.curses.CurseThirstMeter;

/** Curse hooks that need a game event rather than onApply/onTick (mirrors {@link BlessingEventHandler}). */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class CurseEventHandler {
    /** Per-player cooldown for the Loading Screen prank (game time of last trigger). Transient; UUID-keyed. */
    private static final Map<UUID, Long> LAST_LOADING_TRIGGER = new ConcurrentHashMap<>();
    /** Per-player cooldown for the Pacing "dramatic moment". */
    private static final Map<UUID, Long> LAST_PACING_TRIGGER = new ConcurrentHashMap<>();

    private CurseEventHandler() {}

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Super Explosive: a small chance to detonate when taking damage. Guard against explosion damage so
        // a detonation can't recursively re-trigger itself.
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                && EffectManager.isActive(player, Curses.SUPER_EXPLOSIVE)
                && player.getRandom().nextFloat() < CurseSuperExplosive.ON_HIT_CHANCE) {
            ServerLevel level = player.serverLevel();
            level.explode(player, player.getX(), player.getY(), player.getZ(),
                    CurseSuperExplosive.POWER, Level.ExplosionInteraction.MOB);
        }
    }

    @SubscribeEvent
    static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Thirst Meter refill: finishing a Water Bottle tops up thirst (no-op if the curse isn't active).
        if (event.getItem().is(Items.POTION)) {
            PotionContents contents = event.getItem().get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.is(Potions.WATER)) {
                CurseThirstMeter.addThirst(player, CurseThirstMeter.BOTTLE_RESTORE);
            }
        }
        // Gluttony: eating food tops up the extra "second stomach" bar (no-op if the curse isn't active).
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food != null) {
            CurseGluttony.feed(player, food.nutrition());
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Loading Screen: opening a door/trapdoor/fence gate has a chance (on cooldown) to flash the
        // fake fullscreen loading overlay for the cursed player.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(BlockTags.DOORS) && !state.is(BlockTags.TRAPDOORS) && !state.is(BlockTags.FENCE_GATES)) {
            return;
        }
        if (!EffectManager.isActive(player, Curses.LOADING_SCREEN)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        long last = LAST_LOADING_TRIGGER.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now - last < CurseLoadingScreen.COOLDOWN_TICKS || player.getRandom().nextFloat() >= CurseLoadingScreen.CHANCE) {
            return;
        }
        LAST_LOADING_TRIGGER.put(player.getUUID(), now);
        player.setData(WitchModAttachments.LOADING_SCREEN_END_TICK, now + CurseLoadingScreen.DURATION_TICKS);
    }

    @SubscribeEvent
    static void onPacingHit(LivingIncomingDamageEvent event) {
        // Pacing: the cursed player landing a hit has a chance (on cooldown) to trigger a dramatic freeze +
        // camera moment. Listens on the same event as Super Explosive but keys off the attacker, not victim.
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || !EffectManager.isActive(attacker, Curses.PACING)) {
            return;
        }
        long now = attacker.serverLevel().getGameTime();
        long last = LAST_PACING_TRIGGER.getOrDefault(attacker.getUUID(), Long.MIN_VALUE);
        if (now - last < CursePacing.COOLDOWN_TICKS || attacker.getRandom().nextFloat() >= CursePacing.TRIGGER_CHANCE) {
            return;
        }
        LAST_PACING_TRIGGER.put(attacker.getUUID(), now);
        attacker.setData(WitchModAttachments.PACING_END_TICK, now + CursePacing.FREEZE_TICKS);
        EffectUtil.addTimedEffect(attacker, MobEffects.MOVEMENT_SLOWDOWN, CursePacing.FREEZE_TICKS, 6); // dramatic freeze
    }
}
