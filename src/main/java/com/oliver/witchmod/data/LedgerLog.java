package com.oliver.witchmod.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * A lightweight, in-memory record of who cursed/blessed whom (CLAUDE.md section 2.3), including blocked
 * attempts. Deliberately not persisted to disk yet — Phase 4/5 should back this with real SavedData
 * storage once the Ledger block/UI defines exactly what it needs to read; building that now would be
 * premature given the format isn't settled.
 *
 * <p>Stores player <em>names</em> rather than UUIDs — this is a display log for the Ledger UI (Phase 5),
 * not an authoritative record needing offline-safe identity resolution.
 */
public final class LedgerLog {
    private static final int MAX_ENTRIES = 200;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private LedgerLog() {}

    public record Entry(Optional<String> casterName, String targetName, ResourceLocation effectId, String result, long gameTime) {}

    public static void log(Optional<String> casterName, String targetName, ResourceLocation effectId, String result, long gameTime) {
        ENTRIES.addLast(new Entry(casterName, targetName, effectId, result, gameTime));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
        }
    }

    public static List<Entry> recent() {
        return List.copyOf(ENTRIES);
    }
}
