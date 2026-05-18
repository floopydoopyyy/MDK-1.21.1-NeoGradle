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
import java.util.UUID;

/**
 * Handles reading and writing per-player power data to disk.
 */
public class PlayerDataStore {

    // ── Path helpers ──────────────────────────────────────────────────────────

    private static Path getPlayerDir(MinecraftServer server) {
        // getRunDirectory() becomes getServerDirectory() in Mojmap
        return server.getServerDirectory()
                .resolve("loopypowers")
                .resolve("playerdata");
    }

    private static Path getPlayerFile(MinecraftServer server, UUID uuid) {
        return getPlayerDir(server).resolve(uuid + ".dat");
    }

    private static Path getGlobalFile(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("loopypowers")
                .resolve("global.dat");
    }

    // ── Public API (Per-Player) ───────────────────────────────────────────────

    /**
     * Serialises the player's power, level, and cooldowns to disc.
     */
    public static void save(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CompoundTag nbt = new CompoundTag();
        PowerManager.saveToNbt(player, nbt);

        Path file = getPlayerFile(server, player.getUUID());

        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(nbt, file);
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to save player data for {} ({}): {}",
                    player.getName().getString(), player.getUUID(), e.getMessage()
            );
        }
    }

    /**
     * Deserialises and applies power, level, and cooldown data for a player.
     */
    public static void load(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path file = getPlayerFile(server, player.getUUID());
        if (!Files.exists(file)) return;

        try {
            CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (nbt != null) {
                PowerManager.loadFromNbt(player, nbt);
            }
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to load player data for {} ({}): {}",
                    player.getName().getString(), player.getUUID(), e.getMessage()
            );
        }
    }

    /**
     * Deletes the save file for a player.
     */
    public static void delete(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path file = getPlayerFile(server, player.getUUID());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to delete player data for {}: {}",
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