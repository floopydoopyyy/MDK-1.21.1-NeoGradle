package com.loopy.loopypowers.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class BracedEffect extends MobEffect {
    public BracedEffect() {
        // Beneficial effect, color: light gray/white
        super(MobEffectCategory.BENEFICIAL, 0xDDDDDD);
    }
}