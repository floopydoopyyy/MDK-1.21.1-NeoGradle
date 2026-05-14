package com.loopy.loopypowers.manager;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.ui.CooldownUI;
// import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.network.AbilityPackets;
import com.loopy.loopypowers.power.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Assigns powers to players and manages abilities.
 * Cooldowns are managed by CooldownUI; persistence is handled by PlayerDataStore.
 */
public class PowerManager {

    /* ============================================================
       POWER REGISTRY
       ============================================================ */

    private static final PowerInterface SPEED           = new SpeedPower();
    private static final PowerInterface FIRE            = new FirePower();
    private static final PowerInterface TELEPORT        = new TeleportPower();
    private static final PowerInterface LIGHTNING       = new LightningPower();
    private static final PowerInterface FLIGHT          = new FlightPower();
    private static final PowerInterface BLOOD           = new BloodPower();
    private static final PowerInterface SOUND           = new SoundPower();
    private static final PowerInterface STRENGTH        = new StrengthPower();
    private static final PowerInterface EXPLOSION       = new ExplosionPower();
    private static final PowerInterface NATURE          = new NaturePower();
    private static final PowerInterface ICE             = new IcePower();
    private static final PowerInterface FORTUNE         = new FortunePower();
    private static final PowerInterface DARKNESS        = new DarknessPower();
    private static final PowerInterface HEALING_FACTOR  = new HealingPower();
    private static final PowerInterface PSYCHIC         = new PsychicPower();
    private static final PowerInterface COSMIC          = new CosmicPower();
    private static final PowerInterface TELEKINESIS     = new TelekinesisPower();
    private static final PowerInterface INTERDIMENSIONAL = new DimensionalPower();

    private static final List<PowerInterface> ALL_POWERS = List.of(
            SPEED, FIRE, TELEPORT, LIGHTNING, FLIGHT, BLOOD, SOUND, STRENGTH,
            EXPLOSION, NATURE, ICE, FORTUNE, DARKNESS, HEALING_FACTOR,
            PSYCHIC, COSMIC, TELEKINESIS, INTERDIMENSIONAL
    );

    /** UUID → assigned power */
    private static final Map<UUID, PowerInterface> PLAYER_POWERS = new HashMap<>();

    /** UUID → power level (1–3) */
    private static final Map<UUID, Integer> PLAYER_LEVELS = new HashMap<>();

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
     * the new one, then persisting immediately so the change survives a crash.
     * @param silent If true, suppresses the "You gained the power" chat messages.
     * Used during logins and respawns to avoid spam.
     */
    public static void setPower(ServerPlayer player, PowerInterface power, boolean silent) {
        PowerInterface old = getPower(player);
        if (old != null) old.onRemove(player);

        PLAYER_POWERS.put(player.getUUID(), power);
        power.onAssign(player);

        syncClientFlags(player);

        // Persist immediately so admin commands survive crashes
        PlayerDataStore.save(player);

        if (!silent) {
            // TRANSLATED CHAT ANNOUNCEMENTS
            player.sendSystemMessage(Component.translatable("message.loopypowers.power_gained", power.getName()).withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.loopypowers.power_help").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.loopypowers.power_keybinds").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void removePower(ServerPlayer player) {
        PowerInterface current = PLAYER_POWERS.remove(player.getUUID());
        if (current != null) current.onRemove(player);

        clearAllCooldowns(player);
        player.removeAllEffects(); // clearStatusEffects -> removeAllEffects

        // Save the now-empty state so the file also has this
        PlayerDataStore.save(player);
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    /** UUID → (abilityKey → epoch-ms when cooldown expires) */
    private static final Map<UUID, Map<String, Long>> COOLDOWN_END_MS = new HashMap<>();

    private static final Map<String, Long> COOLDOWN_OVERRIDE_MS = new HashMap<>();

    private static double  GLOBAL_CD_MULT    = 1.0;
    private static final Map<UUID, Double> PLAYER_CD_MULT = new HashMap<>();
    private static boolean COOLDOWNS_DISABLED = false;

    private static long nowMs() { return System.currentTimeMillis(); }

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
        COOLDOWN_END_MS.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(key, end);
        CooldownUI.setCooldownEnd(player, key, end, (Component) null);
    }

    public static void clearCooldown(ServerPlayer player, String key) {
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUUID());
        if (map != null) map.remove(key);
        CooldownUI.clearCooldown(player, key);
    }

    public static void clearAllCooldowns(ServerPlayer player) {
        COOLDOWN_END_MS.remove(player.getUUID());
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
        map.put(key, newEnd);
        CooldownUI.setCooldownEnd(player, key, newEnd, (Component) null);
    }

    public static void setCooldownOverride(String key, long durationMs) { COOLDOWN_OVERRIDE_MS.put(key, durationMs); }
    public static void clearCooldownOverride(String key)                 { COOLDOWN_OVERRIDE_MS.remove(key); }
    public static void setGlobalCooldownMultiplier(double mult)          { GLOBAL_CD_MULT = Math.max(0.0, mult); }

    public static void setPlayerCooldownMultiplier(ServerPlayer player, double mult) {
        PLAYER_CD_MULT.put(player.getUUID(), Math.max(0.0, mult));
    }
    public static void clearPlayerCooldownMultiplier(ServerPlayer player) {
        PLAYER_CD_MULT.remove(player.getUUID());
    }

    public static void setCooldownsDisabled(boolean disabled) { COOLDOWNS_DISABLED = disabled; }

    /** Applies overrides and multipliers to produce the final cooldown duration. */
    private static long computeFinalCooldownMs(ServerPlayer player, PowerInterface power, AbilityTypes type, long baseMs) {
        long ms = modifyCooldown(player, power, type, baseMs);

        Long override = COOLDOWN_OVERRIDE_MS.get(abilityKey(power, type));
        if (override != null) ms = override;

        ms = (long) Math.max(0L, ms * GLOBAL_CD_MULT);
        double pm = PLAYER_CD_MULT.getOrDefault(player.getUUID(), 1.0);
        ms = (long) Math.max(0L, ms * pm);

        return ms;
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    public static void usePrimary(ServerPlayer player) {
        PowerInterface power = PLAYER_POWERS.get(player.getUUID());

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
        PowerInterface power = PLAYER_POWERS.get(player.getUUID());

        // No power check
        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.no_power").withStyle(ChatFormatting.RED), 40);
            return;
        }

        // TODO: Uncomment ModEffects.DISPLACED when ported
        // if (player.hasEffect(ModEffects.DISPLACED)) {
        //    CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.displaced").withStyle(ChatFormatting.RED), 20);
        //    return;
        // }

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
        PowerInterface power = PLAYER_POWERS.get(player.getUUID());

        // No power check
        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.no_power").withStyle(ChatFormatting.RED), 40);
            return;
        }

        // TODO: Uncomment ModEffects.DISPLACED when ported
        // if (player.hasEffect(ModEffects.DISPLACED)) {
        //    CooldownUI.pushActionbarOverride(player, Component.translatable("message.loopypowers.displaced").withStyle(ChatFormatting.RED), 20);
        //    return;
        // }

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
        PowerInterface p = PLAYER_POWERS.get(player.getUUID());
        boolean hasStrength = (p instanceof StrengthPower);

        PacketDistributor.sendToPlayer(
                player,
                new AbilityPackets.SyncStrengthPayload(hasStrength)
        );
    }

    private static long modifyCooldown(ServerPlayer player, PowerInterface power, AbilityTypes type, long baseMs) {
        // TODO: Uncomment when StrengthPower is ported
        // if (power instanceof StrengthPower && type != AbilityTypes.ULTIMATE && StrengthPower.isRaging(player)) {
        //    return Math.max(250L, (long)(baseMs * 0.20));
        // }
        return baseMs;
    }

    /* ============================================================
       QUERY
       ============================================================ */

    public static PowerInterface getPower(ServerPlayer player) {
        return PLAYER_POWERS.get(player.getUUID());
    }

    public static String abilityKey(PowerInterface power, AbilityTypes type) {
        return power.getName() + ":" + type.name();
    }

    /* ============================================================
       LEVELS
       ============================================================ */

    public static int getLevel(ServerPlayer player) {
        return PLAYER_LEVELS.getOrDefault(player.getUUID(), 1);
    }

    public static void setLevel(ServerPlayer player, int level) {
        PLAYER_LEVELS.put(player.getUUID(), Math.max(1, Math.min(3, level)));
    }

    public static void levelUp(ServerPlayer player) {
        int current = getLevel(player);
        if (current < 3) {
            setLevel(player, current + 1);
            player.sendSystemMessage(Component.translatable("message.loopypowers.level_up", current + 1).withStyle(ChatFormatting.AQUA));
        }
    }

    /* ============================================================
       PERSISTENCE  (called by PlayerDataStore)
       ============================================================ */

    public static void saveToNbt(ServerPlayer player, CompoundTag nbt) {
        PowerInterface power = getPower(player);
        if (power != null) nbt.putString("lp_power", power.getName());

        nbt.putInt("lp_level", getLevel(player));

        Map<String, Long> cds = COOLDOWN_END_MS.get(player.getUUID());
        if (cds != null && !cds.isEmpty()) {
            CompoundTag cdTag = new CompoundTag();
            for (Map.Entry<String, Long> entry : cds.entrySet()) {
                cdTag.putLong(entry.getKey(), entry.getValue());
            }
            nbt.put("lp_cooldowns", cdTag);
        }
    }

    public static void loadFromNbt(ServerPlayer player, CompoundTag nbt) {
        if (nbt.contains("lp_power")) {
            String name = nbt.getString("lp_power");
            for (PowerInterface p : ALL_POWERS) {
                if (p.getName().equals(name)) {
                    PLAYER_POWERS.put(player.getUUID(), p);
                    p.onAssign(player);
                    syncClientFlags(player);
                    break;
                }
            }
        }

        if (nbt.contains("lp_level")) {
            setLevel(player, nbt.getInt("lp_level"));
        }

        if (nbt.contains("lp_cooldowns")) {
            CompoundTag cdTag = nbt.getCompound("lp_cooldowns");

            Map<String, Long> map = new HashMap<>();
            long now = nowMs();

            for (String key : cdTag.getAllKeys()) {
                long endMs = cdTag.getLong(key);
                if (endMs > now) {
                    map.put(key, endMs);
                    CooldownUI.setCooldownEnd(player, key, endMs, (Component) null);
                }
            }
            COOLDOWN_END_MS.put(player.getUUID(), map);
        }
    }

    public static void copyCooldowns(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        Map<String, Long> oldMap = COOLDOWN_END_MS.get(oldPlayer.getUUID());
        if (oldMap == null) return;
        COOLDOWN_END_MS.put(newPlayer.getUUID(), new HashMap<>(oldMap));
    }

    public static void clearPlayerState(ServerPlayer player) {
        UUID id = player.getUUID();
        PLAYER_POWERS.remove(id);
        PLAYER_LEVELS.remove(id);
        COOLDOWN_END_MS.remove(id);
        PLAYER_CD_MULT.remove(id);
    }
}