package com.loopy.loopypowers.manager;

import com.loopy.loopypowers.Loopypowers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles reading and writing power data to disk.
 *
 * Per-player state (power, level, cd multiplier, passive toggle) is now
 * persisted automatically by NeoForge via the PlayerPowerData attachment
 * registered in ModAttachments. The save()/load() methods below no longer
 * need to touch per-player files; they exist so existing call sites compile
 * without change.
 *
 * Global server state (cooldown on/off, global multiplier) is still written
 * to loopypowers/global.dat by saveGlobal()/loadGlobal() as before.
 */
public class PlayerDataStore {

    // ── Path helpers ──────────────────────────────────────────────────────────

    private static Path getGlobalFile(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("loopypowers")
                .resolve("global.dat");
    }

    // ── Public API (Per-Player) ───────────────────────────────────────────────

    /**
     * No-op: per-player data is now persisted automatically by the NeoForge
     * attachment system. Kept so existing call sites compile without change.
     */
    public static void save(ServerPlayer player) {
        // Attachment data is written into the player entity's own NBT by
        // NeoForge — no manual file write is needed.
    }

    /**
     * Triggers onPlayerLoad so passive effects are re-applied and the client
     * is synced. The attachment data itself is already loaded by NeoForge
     * before PlayerLoggedInEvent fires, so we only need the post-load hook.
     *
     * Previously this was gated on the existence of a loopypowers .dat file,
     * which meant brand-new players (no file yet) never had their power
     * initialised on login. That guard is intentionally removed.
     */
    public static void load(ServerPlayer player) {
        PowerManager.onPlayerLoad(player);
    }

    /**
     * Deletes the legacy per-player .dat file if one exists from a previous
     * version of the mod. Safe to call even if the file is absent.
     */
    public static void deleteLegacyFile(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path file = server.getServerDirectory()
                .resolve("loopypowers")
                .resolve("playerdata")
                .resolve(player.getUUID() + ".dat");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to delete legacy player data for {}: {}",
                    player.getUUID(), e.getMessage()
            );
        }
    }

    // ── Public API (Global) ───────────────────────────────────────────────────

    public static void saveGlobal(MinecraftServer server) {
        if (server == null) return;
        Path file = getGlobalFile(server);
        try {
            Files.createDirectories(file.getParent());
            CompoundTag nbt = PowerManager.saveGlobalState();
            NbtIo.writeCompressed(nbt, file);
        } catch (IOException e) {
            Loopypowers.LOGGER.error("[Loopypowers] Failed to save global data: {}", e.getMessage());
        }
    }

    public static void loadGlobal(MinecraftServer server) {
        if (server == null) return;
        Path file = getGlobalFile(server);
        if (!Files.exists(file)) return;
        try {
            CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (nbt != null) {
                PowerManager.loadGlobalState(nbt);
            }
        } catch (IOException e) {
            Loopypowers.LOGGER.error("[Loopypowers] Failed to load global data: {}", e.getMessage());
        }
    }
}