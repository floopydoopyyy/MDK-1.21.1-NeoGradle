package com.loopy.loopypowers.ritual;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RitualManager {

    public enum RitualType {
        POWER_VESTIGE,
        ELEMENTAL_VESTIGE,
        LIFE_VESTIGE,
        RUIN_VESTIGE,
        MOTION_VESTIGE,
        SPACE_VESTIGE,
        MIND_VESTIGE,
        SEVERANCE_RITUAL,
        REFINED_UPGRADE,
        PERFECTED_UPGRADE
    }

    private static final Map<UUID, RitualInterface> ACTIVE = new HashMap<>();

    public static void startRitual(Player player, RitualType type) {
        ACTIVE.remove(player.getUUID());

        RitualInterface ritual = switch (type) {
            case ELEMENTAL_VESTIGE -> new ElementalRitual(player, type);
            case LIFE_VESTIGE      -> new LifeRitual(player, type);
            case MIND_VESTIGE      -> new MindRitual(player, type);
            case MOTION_VESTIGE    -> new MotionRitual(player, type);
            case RUIN_VESTIGE      -> new RuinRitual(player, type);
            case SPACE_VESTIGE     -> new SpaceRitual(player, type);
            case SEVERANCE_RITUAL  -> new SeveranceRitual(player, type);
            case REFINED_UPGRADE   -> new PowerUpgradeRitual(player, type);
            case PERFECTED_UPGRADE -> new PerfectedUpgradeRitual(player, type);
            default                -> new PowerRitual(player, type);
        };

        ACTIVE.put(player.getUUID(), ritual);
    }

    public static boolean isActive(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    // once per server
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, RitualInterface>> it = ACTIVE.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, RitualInterface> entry = it.next();
            UUID playerId = entry.getKey();
            RitualInterface ritual = entry.getValue();

            // Find the exact player on the server
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player != null) {
                // Tick the ritual exactly once, using the player's current dimension
                boolean done = ritual.tick(player.serverLevel());
                if (done) {
                    it.remove();
                }
            } else {
                // player gone, cancel and remove the ritual
                it.remove();
            }
        }
    }
}