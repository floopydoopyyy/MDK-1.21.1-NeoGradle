package com.oliver.witchmod.data;

import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;

/** New players cannot be targeted for a configurable window after their first-ever join (CLAUDE.md section 7). */
public final class GracePeriod {
    private GracePeriod() {}

    /** Called on every login; only actually sets the timestamp the first time (CLAUDE.md section 7: reconnects don't reset it). */
    public static void markFirstSeenIfAbsent(ServerPlayer player) {
        if (player.getData(WitchModAttachments.FIRST_SEEN_TICK) < 0) {
            player.setData(WitchModAttachments.FIRST_SEEN_TICK, player.serverLevel().getGameTime());
        }
    }

    public static boolean isInGracePeriod(ServerPlayer player) {
        int graceTicks = Config.GRACE_PERIOD_TICKS.get();
        if (graceTicks <= 0) {
            return false;
        }
        long firstSeen = player.getData(WitchModAttachments.FIRST_SEEN_TICK);
        if (firstSeen < 0) {
            // Never recorded (e.g. this player existed before the system was added) — don't block.
            return false;
        }
        return player.serverLevel().getGameTime() - firstSeen < graceTicks;
    }
}
