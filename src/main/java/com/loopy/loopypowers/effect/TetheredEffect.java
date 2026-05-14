package com.loopy.loopypowers.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class TetheredEffect extends MobEffect {

    public TetheredEffect() {
        super(MobEffectCategory.HARMFUL, 0x013220); // dark green
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // handled by nature power, this is purely visual
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // runs every tick
    }
}