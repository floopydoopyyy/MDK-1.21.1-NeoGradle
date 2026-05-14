package com.loopy.loopypowers.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class CompelledEffect extends MobEffect {
    public CompelledEffect() {
        // StatusEffectCategory become MobEffectCategory
        super(MobEffectCategory.HARMFUL, 0xFF1493); // Deep Pink
    }
}