package com.oliver.witchmod.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;

/** Applies, removes, and ticks down curses/blessings on players. The single entry point into {@link ActiveEffects}. */
public final class EffectManager {
    private static Predicate<ServerPlayer> wardCheck = player -> false;
    private static BiConsumer<ServerPlayer, ServerPlayer> onWardDeflect = (target, caster) -> {};
    private static Predicate<ServerPlayer> totemCheck = player -> false;

    private EffectManager() {}

    /**
     * Lets the Ward item (items package) hook into curse application without EffectManager depending on
     * it directly — {@code check} reports whether a player has an active Ward, {@code onDeflect} is
     * called (with target, then attacker) once a deflection actually happens so the item can consume
     * durability and show its directional particles.
     */
    public static void setWardHook(Predicate<ServerPlayer> check, BiConsumer<ServerPlayer, ServerPlayer> onDeflect) {
        wardCheck = check;
        onWardDeflect = onDeflect;
    }

    /**
     * Lets the Warding Totem block (blocks package) hook into curse/blessing application the same way.
     * Unlike the Ward, a Totem blocks both categories completely silently — no redirect, no feedback to
     * the target (CLAUDE.md section 2.4), and applies even to self-casts since it's a full-area shield.
     */
    public static void setTotemHook(Predicate<ServerPlayer> check) {
        totemCheck = check;
    }

    /**
     * Applies {@code effect} to {@code target} for {@code durationTicks}. NO-STACKING with KEEP_LONGER
     * (master-spec Rule 1): if the effect is already active, the instance keeps whichever remaining
     * duration is longer — never additive, never refreshed to a shorter time. Because this is the single
     * entry point every application path goes through (table, commands, coins, jars, Effigy, Bell, Gamble,
     * Mirror backfire), that rule is inherited everywhere for free.
     */
    public static void apply(ServerPlayer target, Holder<Effect> effect, int durationTicks, @Nullable ServerPlayer caster) {
        boolean categoryEnabled = effect.value().category() == EffectCategory.CURSE
                ? Config.CURSES_ENABLED.get()
                : Config.BLESSINGS_ENABLED.get();
        if (!categoryEnabled) {
            if (caster != null) {
                caster.displayClientMessage(Component.literal(
                        (effect.value().category() == EffectCategory.CURSE ? "Curses" : "Blessings") + " are disabled on this server."), true);
            }
            return;
        }
        if (caster != null && caster != target && GracePeriod.isInGracePeriod(target)) {
            caster.displayClientMessage(Component.literal(target.getName().getString() + " is still under a new-player grace period."), true);
            return;
        }
        if (totemCheck.test(target)) {
            return;
        }
        if (effect.value().category() == EffectCategory.CURSE && caster != null && caster != target && wardCheck.test(target)) {
            onWardDeflect.accept(target, caster);
            apply(caster, effect, durationTicks, null);
            return;
        }

        ResourceLocation id = idOf(effect);
        ActiveEffects active = target.getData(WitchModAttachments.ACTIVE_EFFECTS);

        int existingRemaining = active.get(id).map(ActiveEffectInstance::remainingTicks).orElse(0);
        int effectiveDuration = Math.max(existingRemaining, durationTicks);
        // Caster attribution follows the winning duration: a longer (or equal) reapply takes ownership of
        // the instance; if the existing one was longer, its original caster stands.
        Optional<UUID> casterId = durationTicks >= existingRemaining
                ? Optional.ofNullable(caster).map(ServerPlayer::getUUID)
                : active.get(id).flatMap(ActiveEffectInstance::caster);
        active.put(id, new ActiveEffectInstance(effectiveDuration, casterId));
        target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        // Size the effect's own vanilla sub-effects to the kept (never-shortened) duration.
        effect.value().onApply(target, caster, effectiveDuration);
        StatusEffectSync.sync(target);

        // Discovery (CLAUDE.md section 2.7): the victim discovers on trigger, the caster the instant
        // they successfully send it. Centralized here since every application — command, item, Table —
        // passes through this one method.
        DiscoveryManager.markEffectDiscovered(target, id);
        if (caster != null) {
            DiscoveryManager.markEffectDiscovered(caster, id);
        }
    }

    /** @return true if {@code effect} was active on {@code target} and has now been removed. */
    public static boolean remove(ServerPlayer target, Holder<Effect> effect) {
        ResourceLocation id = idOf(effect);
        ActiveEffects active = target.getData(WitchModAttachments.ACTIVE_EFFECTS);
        if (active.remove(id) == null) {
            return false;
        }
        target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        effect.value().onRemove(target);
        StatusEffectSync.sync(target);
        return true;
    }

    /**
     * Removes every active curse/blessing on {@code target}, or only those of {@code category} when given
     * (null = both). Each removed effect's {@link Effect#onRemove} fires so its vanilla sub-effects/state
     * are cleaned up. @return how many were removed.
     */
    public static int removeAll(ServerPlayer target, @Nullable EffectCategory category) {
        ActiveEffects active = target.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (ResourceLocation id : active.activeIds()) {
            Optional<Effect> effect = WitchModRegistries.EFFECT_REGISTRY.getOptional(id);
            if (category != null && !effect.map(e -> e.category() == category).orElse(false)) {
                continue; // category filter: only remove known effects of that category
            }
            if (active.remove(id) != null) {
                effect.ifPresent(e -> e.onRemove(target));
                removed++;
            }
        }
        if (removed > 0) {
            target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
            StatusEffectSync.sync(target);
        }
        return removed;
    }

    /** Purifying Water's "rapidly burns down active curse/blessing timers" (CLAUDE.md section 2.5). */
    public static void reduceAllDurations(ServerPlayer player, int ticksToRemove) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return;
        }
        List<ResourceLocation> expired = active.reduceAll(ticksToRemove);
        player.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        if (!expired.isEmpty()) {
            for (ResourceLocation id : expired) {
                WitchModRegistries.EFFECT_REGISTRY.getOptional(id).ifPresent(effect -> effect.onRemove(player));
            }
            StatusEffectSync.sync(player);
        }
    }

    public static boolean isActive(ServerPlayer target, Holder<Effect> effect) {
        return target.getExistingData(WitchModAttachments.ACTIVE_EFFECTS)
                .map(active -> active.isActive(idOf(effect)))
                .orElse(false);
    }

    /** Total curses+blessings currently active on {@code target} — CLAUDE.md section 2.7's per-player limit. */
    public static int activeCount(ServerPlayer target) {
        return target.getExistingData(WitchModAttachments.ACTIVE_EFFECTS)
                .map(ActiveEffects::size)
                .orElse(0);
    }

    /** Called once per player per tick by {@link WitchModEventHandler}. */
    public static void tick(ServerPlayer player) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return;
        }

        for (ResourceLocation id : active.activeIds()) {
            active.get(id).ifPresent(instance -> WitchModRegistries.EFFECT_REGISTRY.getOptional(id)
                    .ifPresent(effect -> effect.onTick(player, instance.remainingTicks())));
        }

        List<ResourceLocation> expired = active.tickDown();
        if (!expired.isEmpty()) {
            player.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
            for (ResourceLocation id : expired) {
                WitchModRegistries.EFFECT_REGISTRY.getOptional(id).ifPresent(effect -> effect.onRemove(player));
            }
            StatusEffectSync.sync(player);
        }
    }

    private static ResourceLocation idOf(Holder<Effect> effect) {
        return effect.unwrapKey().orElseThrow().location();
    }
}
