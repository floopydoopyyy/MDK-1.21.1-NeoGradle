package com.loopy.loopypowers.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class BloodBoundEffect extends MobEffect {

    public BloodBoundEffect() {
        super(MobEffectCategory.HARMFUL, 0x5C1010); // red
    }

    // 1.21.1 MOJMAP: Replaces applyUpdateEffect
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // handled by blood power, this is purely visual
        return true;
    }

    // 1.21.1 MOJMAP: Replaces canApplyUpdateEffect
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // runs every tick
    }
}