package com.loopy.loopypowers.effect;

// import com.loopy.loopypowers.power.CosmicPower;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class FateEffect extends MobEffect {

    public FateEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFFFFF); // white
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // handled by cosmic power, this is purely visual
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // runs every tick
    }
}