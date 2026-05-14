package com.loopy.loopypowers.effect;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

public class BleedEffect extends MobEffect {

    public BleedEffect() {
        super(MobEffectCategory.HARMFUL, 0x5C1010);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide && entity.level().getGameTime() % 10 == 0) {
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.0f), 0.8f),
                        entity.getX(), entity.getY(0.5), entity.getZ(),
                        1, 0.2, 0.4, 0.2, 0.01
                );
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}