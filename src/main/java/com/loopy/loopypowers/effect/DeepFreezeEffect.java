package com.loopy.loopypowers.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class DeepFreezeEffect extends MobEffect {

    public DeepFreezeEffect() {
        super(MobEffectCategory.HARMFUL, 0x00BFFF); // light blue
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // handled by ice power, this is purely visual
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // runs every tick
    }
}