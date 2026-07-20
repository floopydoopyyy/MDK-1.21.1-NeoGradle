package com.oliver.witchmod.data;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.oliver.witchmod.WitchMod;

/** Small shared helpers for {@link Effect} implementations — kept out of the base class to keep its contract pure. */
public final class EffectUtil {
    private EffectUtil() {}

    public static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, path);
    }

    public static void addModifier(ServerPlayer target, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    public static void removeModifier(ServerPlayer target, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /** Applies a vanilla status effect sized to {@code durationTicks} so it tracks our own duration, not vanilla's. */
    public static void addTimedEffect(ServerPlayer target, Holder<MobEffect> mobEffect, int durationTicks, int amplifier) {
        target.addEffect(new MobEffectInstance(mobEffect, durationTicks, amplifier, false, true));
    }

    public static void removeTimedEffect(ServerPlayer target, Holder<MobEffect> mobEffect) {
        target.removeEffect(mobEffect);
    }

    /** {@code true} every {@code intervalTicks}, using the effect's own remaining-ticks countdown as the clock. */
    public static boolean every(int ticksRemaining, int intervalTicks) {
        return ticksRemaining % intervalTicks == 0;
    }

    public static boolean chance(RandomSource random, float probability) {
        return random.nextFloat() < probability;
    }
}
