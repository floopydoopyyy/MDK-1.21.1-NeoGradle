package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;

/**
 * The set of curses/blessings currently active on one player. Persisted per-player (see
 * {@link WitchModAttachments#ACTIVE_EFFECTS}); mutated only through {@link EffectManager}.
 */
public final class ActiveEffects {
    public static final Codec<ActiveEffects> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, ActiveEffectInstance.CODEC)
            .xmap(ActiveEffects::new, ActiveEffects::asMap);

    private final Map<ResourceLocation, ActiveEffectInstance> effects;

    private ActiveEffects(Map<ResourceLocation, ActiveEffectInstance> effects) {
        this.effects = new LinkedHashMap<>(effects);
    }

    public static ActiveEffects empty() {
        return new ActiveEffects(Map.of());
    }

    private Map<ResourceLocation, ActiveEffectInstance> asMap() {
        return effects;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public boolean isActive(ResourceLocation id) {
        return effects.containsKey(id);
    }

    public Optional<ActiveEffectInstance> get(ResourceLocation id) {
        return Optional.ofNullable(effects.get(id));
    }

    public Set<ResourceLocation> activeIds() {
        return Set.copyOf(effects.keySet());
    }

    public int size() {
        return effects.size();
    }

    void put(ResourceLocation id, ActiveEffectInstance instance) {
        effects.put(id, instance);
    }

    ActiveEffectInstance remove(ResourceLocation id) {
        return effects.remove(id);
    }

    /** Decrements every active effect by one tick and returns the ids of any that just expired. */
    List<ResourceLocation> tickDown() {
        return reduceAll(1);
    }

    /** Decrements every active effect by {@code amount} ticks and returns the ids of any that just expired. */
    List<ResourceLocation> reduceAll(int amount) {
        List<ResourceLocation> expired = new ArrayList<>();
        for (var entry : effects.entrySet()) {
            int remaining = entry.getValue().remainingTicks() - amount;
            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(entry.getValue().withRemainingTicks(remaining));
            }
        }
        expired.forEach(effects::remove);
        return expired;
    }
}
