package com.oliver.witchmod.data;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * One player's ongoing brush with a single {@link Effect}: how long it has left, and who cast it
 * (absent for command/system-applied effects).
 */
public record ActiveEffectInstance(int remainingTicks, Optional<UUID> caster) {
    public static final Codec<ActiveEffectInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("remaining_ticks").forGetter(ActiveEffectInstance::remainingTicks),
            UUIDUtil.CODEC.optionalFieldOf("caster").forGetter(ActiveEffectInstance::caster))
            .apply(instance, ActiveEffectInstance::new));

    ActiveEffectInstance withRemainingTicks(int newRemainingTicks) {
        return new ActiveEffectInstance(newRemainingTicks, caster);
    }
}
