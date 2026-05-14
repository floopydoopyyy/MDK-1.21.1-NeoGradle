package com.loopy.loopypowers.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.*;
import com.loopy.loopypowers.manager.AbilityTypes;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.PowerInterface;
import com.loopy.loopypowers.manager.PassiveManager;

// now just displays cooldowns to the user. actual cooldowns handled by power manager.
public final class CooldownUI {

    private static final class Entry {
        long endMs;
        Component suffix; // CHANGED to Component

        Entry(long endMs, Component suffix) {
            this.endMs = endMs;
            this.suffix = suffix;
        }
    }

    // temporary surpress actionbar
    private static final class OverrideEntry {
        long endMs;
        Component message;

        OverrideEntry(long endMs, Component message) {
            this.endMs = endMs;
            this.message = message;
        }
    }

    private static final Map<UUID, OverrideEntry> OVERRIDES = new HashMap<>();

    // now should store charges
    private static final Map<UUID, Map<String, Entry>> COOLDOWNS = new HashMap<>();

    private CooldownUI() {
    } // no instances

    /* ============================================================
       PUBLIC API (used by ALL powers)
       ============================================================ */

    // actionbar surpresion
    public static void pushActionbarOverride(ServerPlayer player, Component message, int ticks) {
        if (ticks <= 0) return;
        long end = System.currentTimeMillis() + (ticks * 50L);
        OVERRIDES.put(player.getUUID(), new OverrideEntry(end, message));
    }

    // read-only snapshot
    public static final class CooldownInfo {
        public final long remainingMs;
        public final String suffix;

        public CooldownInfo(long remainingMs, String suffix) {
            this.remainingMs = remainingMs;
            this.suffix = suffix;
        }
    }

    // query
    public static Map<String, CooldownInfo> getCooldownSnapshot(ServerPlayer player) {
        UUID id = player.getUUID();

        Map<String, Entry> map = COOLDOWNS.get(id);
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }

        long now = System.currentTimeMillis();
        Map<String, CooldownInfo> out = new LinkedHashMap<>();

        for (var e : map.entrySet()) {
            long remaining = e.getValue().endMs - now;
            if (remaining > 0) {
                // Fallback for snapshot using string
                String strSuffix = e.getValue().suffix == null ? null : e.getValue().suffix.getString();
                out.put(e.getKey(), new CooldownInfo(remaining, strSuffix));
            }
        }

        return out;
    }

    // same signature
    public static void startCooldown(ServerPlayer player, String key, long durationMs) {
        COOLDOWNS
                .computeIfAbsent(player.getUUID(), u -> new HashMap<>())
                .put(key, new Entry(System.currentTimeMillis() + durationMs, null));
    }

    // suffix for charges (Component version)
    public static void startCooldown(ServerPlayer player, String key, long durationMs, Component suffix) {
        COOLDOWNS
                .computeIfAbsent(player.getUUID(), u -> new HashMap<>())
                .put(key, new Entry(System.currentTimeMillis() + durationMs, suffix));
    }

    // Legacy String fallback for older power classes
    public static void startCooldown(ServerPlayer player, String key, long durationMs, String suffix) {
        startCooldown(player, key, durationMs, suffix == null ? null : Component.literal(suffix));
    }

    // absolute end for cooldowns (Component version)
    public static void setCooldownEnd(ServerPlayer player, String key, long endMs, Component suffix) {
        COOLDOWNS
                .computeIfAbsent(player.getUUID(), u -> new HashMap<>())
                .put(key, new Entry(endMs, suffix));
    }

    // Legacy String fallback
    public static void setCooldownEnd(ServerPlayer player, String key, long endMs, String suffix) {
        setCooldownEnd(player, key, endMs, suffix == null ? null : Component.literal(suffix));
    }

    // clear specific entries when full.
    public static void clearCooldown(ServerPlayer player, String key) {
        Map<String, Entry> map = COOLDOWNS.get(player.getUUID());
        if (map == null) return;
        map.remove(key);
    }

    public static void clearAllCooldowns(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }

    // CHANGED: Now returns a translatable Component object instead of a String
    public static Component makeChargeSuffix(int currentCharges, int maxCharges, int nextChargeTicks, int rechargeTicksPerCharge) {
        if (maxCharges <= 0) return Component.empty();
        currentCharges = Math.max(0, Math.min(currentCharges, maxCharges));

        int missing = maxCharges - currentCharges;
        if (missing <= 0) {
            return Component.translatable("hud.loopypowers.charges_simple", currentCharges, maxCharges);
        }

        nextChargeTicks = Math.max(0, nextChargeTicks);
        rechargeTicksPerCharge = Math.max(1, rechargeTicksPerCharge);

        // how long until fully recharged
        int fullTicks = nextChargeTicks + Math.max(0, missing - 1) * rechargeTicksPerCharge;

        long nextSec = (long) Math.ceil(nextChargeTicks / 20.0);
        long fullSec = (long) Math.ceil(fullTicks / 20.0);

        if (missing == 1) {
            return Component.translatable("hud.loopypowers.charges_next", currentCharges, maxCharges, nextSec);
        }
        return Component.translatable("hud.loopypowers.charges_full", currentCharges, maxCharges, nextSec, fullSec);
    }

    public static boolean isReady(ServerPlayer player, String key) {
        Map<String, Entry> map = COOLDOWNS.get(player.getUUID());
        if (map == null) return true;

        Entry e = map.get(key);
        return e == null || e.endMs <= System.currentTimeMillis();
    }

    private static String formatAbility(ServerPlayer player, String key) {
        String[] parts = key.split(":");
        if (parts.length != 2) return key;

        String powerName = parts[0];
        AbilityTypes type = AbilityTypes.valueOf(parts[1]);

        PowerInterface power = PowerManager.getPower(player);
        if (power == null) return key;

        String abilityName = switch (type) {
            case PRIMARY -> power.getPrimaryName();
            case SECONDARY -> power.getSecondaryName();
            case ULTIMATE -> power.getUltimateName();
        };

        return type.color + abilityName;
    }

    /* ============================================================
       TICK HANDLER (called once per server tick per player)
       ============================================================ */

    public static void tick(ServerPlayer player) {
        if (player.level().getGameTime() % 10 != 0) return;
        cleanup(player);
        renderActionBar(player);
    }

    /* ============================================================
       INTERNALS
       ============================================================ */

    private static void cleanup(ServerPlayer player) {
        UUID id = player.getUUID();

        // cleanup cooldowns
        Map<String, Entry> map = COOLDOWNS.get(id);
        if (map != null) {
            Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
            long now = System.currentTimeMillis();
            while (it.hasNext()) {
                if (it.next().getValue().endMs <= now) {
                    it.remove();
                }
            }
            if (map.isEmpty()) COOLDOWNS.remove(id);
        }

        // cleanup override
        OverrideEntry ov = OVERRIDES.get(id);
        if (ov != null && ov.endMs <= System.currentTimeMillis()) {
            OVERRIDES.remove(id);
        }
    }

    private static void renderActionBar(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();

        // 1) OVERRIDE wins
        OverrideEntry ov = OVERRIDES.get(id);
        if (ov != null && ov.endMs > now && ov.message != null) {
            player.displayClientMessage(ov.message, true);
            return;
        }

        // 2) suppression
        if (player.getTags().contains("suppress_actionbar")) return;

        Map<String, Entry> map = COOLDOWNS.get(id);
        boolean hasCooldowns = map != null && !map.isEmpty();

        // passive state
        boolean passiveOff = !PassiveManager.isEnabled(player);

        // only show if cooldowns exist
        if (!hasCooldowns) return;

        // CHANGED: We now stitch Component objects together instead of a StringBuilder
        MutableComponent bar = Component.empty();

        // passive indicator when off
        if (passiveOff) {
            bar.append(Component.translatable("hud.loopypowers.passive_off").withStyle(ChatFormatting.DARK_GRAY));
            bar.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        }

        for (var entry : map.entrySet()) {
            Entry e = entry.getValue();
            long remaining = e.endMs - now;

            bar.append(Component.literal(formatAbility(player, entry.getKey())))
                    .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY));

            if (remaining <= 0) {
                bar.append(Component.translatable("hud.loopypowers.ready").withStyle(ChatFormatting.GREEN));
            } else {
                bar.append(Component.translatable("hud.loopypowers.cooldown_seconds", remaining / 1000).withStyle(ChatFormatting.RED));
            }

            if (e.suffix != null) {
                bar.append(Component.literal(" "))
                        .append(e.suffix.copy().withStyle(ChatFormatting.DARK_GRAY));
            }

            bar.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        }

        player.displayClientMessage(bar, true);
    }
}