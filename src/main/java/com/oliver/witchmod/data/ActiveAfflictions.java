package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;

/**
 * The set of Global events currently "afflicting" one player (CLAUDE.md section 2.6's Afflicted
 * status). Parallels {@link ActiveEffects} but simpler — no caster to track, since globals need no
 * selector. Mutated only through {@link AfflictionManager}.
 */
public final class ActiveAfflictions {
    public static final Codec<ActiveAfflictions> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
            .xmap(ActiveAfflictions::new, ActiveAfflictions::asMap);

    private final Map<ResourceLocation, Integer> remainingTicksById;

    private ActiveAfflictions(Map<ResourceLocation, Integer> remainingTicksById) {
        this.remainingTicksById = new LinkedHashMap<>(remainingTicksById);
    }

    public static ActiveAfflictions empty() {
        return new ActiveAfflictions(Map.of());
    }

    private Map<ResourceLocation, Integer> asMap() {
        return remainingTicksById;
    }

    public boolean isEmpty() {
        return remainingTicksById.isEmpty();
    }

    public int maxRemaining() {
        return remainingTicksById.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    void put(ResourceLocation id, int remainingTicks) {
        remainingTicksById.put(id, remainingTicks);
    }

    /** Decrements every active affliction by one tick and returns the ids of any that just expired. */
    List<ResourceLocation> tickDown() {
        List<ResourceLocation> expired = new ArrayList<>();
        for (var entry : remainingTicksById.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }
        expired.forEach(remainingTicksById::remove);
        return expired;
    }
}
