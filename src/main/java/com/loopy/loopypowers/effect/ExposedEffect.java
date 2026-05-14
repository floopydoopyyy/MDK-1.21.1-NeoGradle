package com.loopy.loopypowers.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class ExposedEffect extends MobEffect {

    public ExposedEffect() {
        super(MobEffectCategory.HARMFUL, 0x000000); // black
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // handled by darkness power, this is purely visual
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // runs every tick
    }
}