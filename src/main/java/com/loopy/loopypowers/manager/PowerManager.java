package com.loopy.loopypowers.manager;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.ui.CooldownUI;
import com.loopy.loopypowers.network.AbilityPackets;
import com.loopy.loopypowers.power.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Assigns powers to players and manages abilities.
 * Cooldowns are managed by CooldownUI; persistence is handled via NeoForge
 * entity attachments (ModAttachments.POWER_DATA), which automatically scope
 * player data per-world-save instead of leaking across saves via static maps.
 *
 * Global server state (cooldown toggles/multipliers) is still persisted by
 * PlayerDataStore as before.
 */
public class PowerManager {

    /* ============================================================
       POWER REGISTRY
       ============================================================ */

    private static final PowerInterface SPEED            = new SpeedPower();
    private static final PowerInterface FIRE             = new FirePower();
    private static final PowerInterface TELEPORT         = new TeleportPower();
    private static final PowerInterface LIGHTNING        = new LightningPower();
    private static final PowerInterface FLIGHT           = new FlightPower();
    private static final PowerInterface BLOOD            = new BloodPower();
    private static final PowerInterface SOUND            = new SoundPower();
    private static final PowerInterface STRENGTH         = new StrengthPower();
    private static final PowerInterface EXPLOSION        = new ExplosionPower();
    private static final PowerInterface NATURE           = new NaturePower();
    private static final PowerInterface ICE              = new IcePower();
    private static final PowerInterface FORTUNE          = new FortunePower();
    private static final PowerInterface DARKNESS         = new DarknessPower();
    private static final PowerInterface HEALING_FACTOR   = new HealingPower();
    private static final PowerInterface PSYCHIC          = new PsychicPower();
    private static final PowerInterface COSMIC           = new CosmicPower();
    private static final PowerInterface TELEKINESIS      = new TelekinesisPower();
    private static final PowerInterface INTERDIMENSIONAL = new DimensionalPower();

    private static final List<PowerInterface> ALL_POWERS = List.of(
            SPEED, FIRE, TELEPORT, LIGHTNING, FLIGHT, BLOOD, SOUND, STRENGTH,
            EXPLOSION, NATURE, ICE, FORTUNE, DARKNESS, HEALING_FACTOR,
            PSYCHIC, COSMIC, TELEKINESIS, INTERDIMENSIONAL
    );

    /* ============================================================
       ATTACHMENT HELPER
       ============================================================ */

    /** Returns the attachment for this player, creating it with defaults if absent. */
    private static PlayerPowerData data(ServerPlayer player) {
        return player.getData(ModAttachments.POWER_DATA);
    }

    /** Resolves the stored power name back to a PowerInterface, or null if none. */
    private static PowerInterface resolvePower(String name) {
        if (name == null) return null;
        for (PowerInterface p : ALL_POWERS) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    /* ============================================================
       POWER ASSIGNMENT
       ============================================================ */

    public static void assignRandomPower(ServerPlayer player) {
        PowerInterface power = ALL_POWERS.get(new Random().nextInt(ALL_POWERS.size()));
        setPower(player, power);
    }

    /**
     * Default alias for setting a power. Triggers the chat announcements.
     */
    public static void setPower(ServerPlayer player, PowerInterface power) {
        setPower(player, power, false);
    }

    /**
     * Sets a player's power, calling onRemove on the old one and onAssign on
     * the new one. The change is persisted automatically via the NeoForge
     * attachment system — no explicit save call is required.
     *
     * @param silent If true, suppresses the "You gained the power" chat messages.
     *               Used during logins and respawns to avoid spam.
     */
    public static void setPower(ServerPlayer player, PowerInterface power, boolean silent) {
        PowerInterface old = getPower(player);
        if (old != null) old.onRemove(player);

        data(player).setPowerName(power.getName());
        power.onAssign(player);

        syncClientFlags(player);

        if (!silent) {
            // TRANSLATED CHAT ANNOUNCEMENTS
            player.sendSystemMessage(Component.translatable("message.loopypowers.power_gained", power.getName()).withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.loopypowers.power_help").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.loopypowers.power_keybinds").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void removePower(ServerPlayer player) {
        PowerInterface current = getPower(player);
        if (current != null) current.onRemove(player);

        data(player).clearPower();

        clearAllCooldowns(player);
        player.removeAllEffects();
    }

    /* ============================================================
       COOLDOWNS
       Persisted in the PlayerPowerData attachment so they survive
       world reloads. The static map below is a fast in-memory cache;
       the attachment is the source of truth written to NBT on logout.
       ============================================================ */

    /** Fast in-memory cache: UUID → (abilityKey → epoch-ms expiry).
     *  Populated from the attachment on login, written back on every change. */
    private static final Map<UUID, Map<String, Long>> COOLDOWN_END_MS = new HashMap<>();

    private static final Map<String, Long> COOLDOWN_OVERRIDE_MS = new HashMap<>();

    private static double  GLOBAL_CD_MULT    = 1.0;
    private static boolean COOLDOWNS_DISABLED = false;

    private static long nowMs() { return System.currentTimeMillis(); }

    /** Writes a cooldown to both the in-memory cache and the persistent attachment. */
    private static void putCooldown(ServerPlayer player, String key, long endMs) {
        COOLDOWN_END_MS.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(key, endMs);
        data(player).putCooldown(key, endMs);
    }

    /** Removes a cooldown from both the cache and the attachment. */
    private static void dropCooldown(ServerPlayer player, String key) {
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUUID());
        if (map != null) map.remove(key);
        data(player).removeCooldown(key);
    }

    private static long getCooldownEndMs(ServerPlayer player, String key) {
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUUID());
        return (map == null) ? 0L : map.getOrDefault(key, 0L);
    }

    public static long getCooldownRemainingMs(ServerPlayer player, String key) {
        return Math.max(0L, getCooldownEndMs(player, key) - nowMs());
    }

    public static boolean isCooldownReady(ServerPlayer player, String key) {
        if (COOLDOWNS_DISABLED) return true;
        return getCooldownRemainingMs(player, key) <= 0L;
    }

    public static void startCooldown(ServerPlayer player, String key, long durationMs) {
        if (COOLDOWNS_DISABLED) return;
        if (durationMs <= 0L) { clearCooldown(player, key); return; }

        long end = nowMs() + durationMs;
        putCooldown(player, key, end);
        CooldownUI.setCooldownEnd(player, key, end, (Component) null);
    }

    public static void clearCooldown(ServerPlayer player, String key) {
        dropCooldown(player, key);
        CooldownUI.clearCooldown(player, key);
    }

    public static void clearAllCooldowns(ServerPlayer player) {
        COOLDOWN_END_MS.remove(player.getUUID());
        data(player).clearCooldowns();
        CooldownUI.clearAllCooldowns(player);
    }

    public static void clearAbilityCooldown(ServerPlayer player, AbilityTypes type) {
        PowerInterface power = getPower(player);
        if (power == null) return;
        clearCooldown(player, abilityKey(power, type));
    }

    public static void clearAllAbilityCooldowns(ServerPlayer player) {
        PowerInterface power = getPower(player);
        if (power == null) return;
        clearCooldown(player, abilityKey(power, AbilityTypes.PRIMARY));
        clearCooldown(player, abilityKey(power, AbilityTypes.SECONDARY));
        clearCooldown(player, abilityKey(power, AbilityTypes.ULTIMATE));
    }

    public static void reduceAllCooldowns(ServerPlayer player, long amountMs) {
        if (amountMs <= 0) return;
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUUID());
        if (map == null || map.isEmpty()) return;

        long now = nowMs();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            long currentEnd = entry.getValue();
            if (currentEnd <= now) continue;

            long newEnd = Math.max(now, currentEnd - amountMs);
            entry.setValue(newEnd);
            data(player).putCooldown(entry.getKey(), newEnd);
            CooldownUI.setCooldownEnd(player, entry.getKey(), newEnd, (Component) null);
        }
    }

    public static void reduceSingleCooldown(ServerPlayer player, String key, long amountMs) {
        if (amountMs <= 0) return;
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUUID());
        if (map == null) return;

        Long currentEnd = map.get(key);
        if (currentEnd == null) return;

        long now = nowMs();
        if (currentEnd <= now) return;

        long newEnd = Math.max(now, currentEnd - amountMs);
        putCooldown(player, key, newEnd);
        CooldownUI.setCooldownEnd(player, key, newEnd, (Component) null);
    }

    public static void setCooldownOverride(String key, long durationMs) { COOLDOWN_OVERRIDE_MS.put(key, durationMs); }
    public static void clearCooldownOverride(String key)                 { COOLDOWN_OVERRIDE_MS.remove(key); }
    public static void setGlobalCooldownMultiplier(double mult)          { GLOBAL_CD_MULT = Math.max(0.0, mult); }

    public static void setPlayerCooldownMultiplier(ServerPlayer player, double mult) {
        data(player).setCdMult(Math.max(0.0, mult));
    }

    public static void clearPlayerCooldownMultiplier(ServerPlayer player) {
        data(player).setCdMult(1.0);
    }

    public static void setCooldownsDisabled(boolean disabled) { COOLDOWNS_DISABLED = disabled; }

    /** Applies overrides and multipliers to produce the final cooldown duration. */
    private static long computeFinalCooldownMs(ServerPlayer player, PowerInterface power, AbilityTypes type, long baseMs) {
        long ms = modifyCooldown(player, power, type, baseMs);

        Long override = COOLDOWN_OVERRIDE_MS.get(abilityKey(power, type));
        if (override != null) ms = override;

        ms = (long) Math.max(0L, ms * GLOBAL_CD_MULT);
        double pm = data(player).getCdMult();
        ms = (long) Math.max(0L, ms * pm);

        return ms;
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    public static void usePrimary(ServerPlayer player) {
        PowerInterface power = getPower(player);

        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.no_power").withStyle(ChatFormatting.RED), 40);
            return;
        }

        if (player.hasEffect(ModEffects.DISPLACED) && !(power instanceof HealingPower)) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.displaced").withStyle(ChatFormatting.RED), 20);
            return;
        }

        String key = abilityKey(power, AbilityTypes.PRIMARY);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivatePrimary(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.PRIMARY, power.getPrimaryCooldownMs());
            startCooldown(player, key, cd);
        }
    }

    public static void useSecondary(ServerPlayer player) {
        PowerInterface power = getPower(player);

        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.no_power").withStyle(ChatFormatting.RED), 40);
            return;
        }

        if (player.hasEffect(ModEffects.DISPLACED)) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.displaced").withStyle(ChatFormatting.RED), 20);
            return;
        }

        // Level check
        if (getLevel(player) < 2) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.secondary_level_req").withStyle(ChatFormatting.RED), 30);
            return;
        }

        String key = abilityKey(power, AbilityTypes.SECONDARY);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivateSecondary(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.SECONDARY, power.getSecondaryCooldownMs());
            startCooldown(player, key, cd);
        }
    }

    public static void useUltimate(ServerPlayer player) {
        PowerInterface power = getPower(player);

        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.no_power").withStyle(ChatFormatting.RED), 40);
            return;
        }

        if (player.hasEffect(ModEffects.DISPLACED)) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.displaced").withStyle(ChatFormatting.RED), 20);
            return;
        }

        // Level check
        if (getLevel(player) < 3) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.ultimate_level_req").withStyle(ChatFormatting.RED), 30);
            return;
        }

        String key = abilityKey(power, AbilityTypes.ULTIMATE);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivateUltimate(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.ULTIMATE, power.getUltimateCooldownMs());
            startCooldown(player, key, cd);
        }
    }

    private static void syncClientFlags(ServerPlayer player) {
        PowerInterface p = getPower(player);
        boolean hasStrength = (p instanceof StrengthPower);

        PacketDistributor.sendToPlayer(
                player,
                new AbilityPackets.SyncStrengthPayload(hasStrength)
        );
    }

    private static long modifyCooldown(ServerPlayer player, PowerInterface power, AbilityTypes type, long baseMs) {
        if (power instanceof StrengthPower && type != AbilityTypes.ULTIMATE && StrengthPower.isRaging(player)) {
            return Math.max(250L, (long)(baseMs * 0.15));
        }
        return baseMs;
    }

    /* ============================================================
       QUERY
       ============================================================ */

    public static PowerInterface getPower(ServerPlayer player) {
        return resolvePower(data(player).getPowerName());
    }

    public static String abilityKey(PowerInterface power, AbilityTypes type) {
        return power.getName() + ":" + type.name();
    }

    /* ============================================================
       LEVELS
       ============================================================ */

    public static int getLevel(ServerPlayer player) {
        return data(player).getLevel();
    }

    public static void setLevel(ServerPlayer player, int level) {
        data(player).setLevel(level);
    }

    public static void levelUp(ServerPlayer player) {
        int current = getLevel(player);
        if (current < 3) {
            setLevel(player, current + 1);
            player.sendSystemMessage(Component.translatable("message.loopypowers.level_up", current + 1).withStyle(ChatFormatting.AQUA));
        }
    }

    /* ============================================================
       GLOBAL STATE PERSISTENCE  (server-wide settings only)
       Per-player data is now handled automatically by the attachment
       system and no longer needs explicit save/load calls here.
       ============================================================ */

    private static boolean globalStateLoaded = false;

    public static void checkLoadGlobalState(MinecraftServer server) {
        if (!globalStateLoaded && server != null) {
            PlayerDataStore.loadGlobal(server);
            globalStateLoaded = true;
        }
    }

    public static CompoundTag saveGlobalState() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("cooldowns_disabled", COOLDOWNS_DISABLED);
        tag.putDouble("global_cd_mult", GLOBAL_CD_MULT);
        return tag;
    }

    public static void loadGlobalState(CompoundTag tag) {
        if (tag.contains("cooldowns_disabled")) COOLDOWNS_DISABLED = tag.getBoolean("cooldowns_disabled");
        if (tag.contains("global_cd_mult")) GLOBAL_CD_MULT = tag.getDouble("global_cd_mult");
    }

    /**
     * Called on first player join to trigger global state loading.
     * Per-player data is loaded automatically by NeoForge via the attachment;
     * we only need to run onAssign so passive effects are re-applied.
     */
    public static void onPlayerLoad(ServerPlayer player) {
        checkLoadGlobalState(player.getServer());

        // Restore persisted cooldowns from attachment into the in-memory cache.
        // Expired entries were already stripped during deserializeNBT so we
        // don't need to filter here.
        PlayerPowerData pd = data(player);
        Map<String, Long> stored = pd.getCooldownEndMs();
        if (!stored.isEmpty()) {
            COOLDOWN_END_MS.put(player.getUUID(), new HashMap<>(stored));
            // Sync each active cooldown to the HUD
            long now = nowMs();
            for (Map.Entry<String, Long> e : stored.entrySet()) {
                if (e.getValue() > now) {
                    CooldownUI.setCooldownEnd(player, e.getKey(), e.getValue(), (Component) null);
                }
            }
        }

        // Re-apply the power's passive effects now that the player entity exists.
        PowerInterface power = getPower(player);
        if (power != null) {
            power.onAssign(player);
            syncClientFlags(player);
        }

        // Restore passive toggle state into PassiveManager's in-memory tracking.
        PassiveManager.setPassiveState(player, pd.isPassive());
    }

    public static void copyCooldowns(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        Map<String, Long> oldMap = COOLDOWN_END_MS.get(oldPlayer.getUUID());
        if (oldMap == null) return;
        Map<String, Long> copy = new HashMap<>(oldMap);
        COOLDOWN_END_MS.put(newPlayer.getUUID(), copy);
        // Mirror into the attachment so they persist on the new entity too
        PlayerPowerData newData = data(newPlayer);
        newData.clearCooldowns();
        copy.forEach(newData::putCooldown);
    }

    /**
     * Clears only in-memory runtime state on disconnect.
     * Power, level, cd multiplier, and passive toggle are preserved.
     * Cooldowns are intentionally left in the attachment so they
     * survive the reload — the cache is repopulated in onPlayerLoad.
     */
    public static void clearPlayerState(ServerPlayer player) {
        COOLDOWN_END_MS.remove(player.getUUID());
        PassiveManager.setPassiveState(player, true);
    }

    /**
     * Fully strips a player's power and resets all persistent data.
     * Use this when you intentionally want to remove someone's power
     * (e.g. admin command, ritual reset). NOT called on disconnect.
     */
    public static void fullResetPlayer(ServerPlayer player) {
        PowerInterface current = getPower(player);
        if (current != null) current.onRemove(player);

        data(player).clearPower();
        data(player).setLevel(1);
        data(player).setCdMult(1.0);
        data(player).setPassive(true);
        data(player).clearCooldowns();

        COOLDOWN_END_MS.remove(player.getUUID());
        CooldownUI.clearAllCooldowns(player);
        PassiveManager.setPassiveState(player, true);
    }

    public static boolean areCooldownsDisabled() {
        return COOLDOWNS_DISABLED;
    }

    public static int getModifiedCooldownTicks(ServerPlayer player, int baseTicks) {
        if (COOLDOWNS_DISABLED) return 0;

        double mult = GLOBAL_CD_MULT * data(player).getCdMult();
        return (int) Math.max(0, Math.round(baseTicks * mult));
    }
}