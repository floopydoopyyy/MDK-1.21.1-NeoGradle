package com.loopy.loopypowers.effect;

import com.loopy.loopypowers.damage.ModDamageTypes;
// import com.loopy.loopypowers.network.RenderPackets;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

public class FracturedEffect extends MobEffect {
    public FracturedEffect() {
        super(MobEffectCategory.HARMFUL, 0x4B0082); // Dark Violet

        // 1.21.1 Attribute Modifier Mojmap Translation
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath("loopypowers", "fractured_slowness"),
                -0.50d,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // tick every frame
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;

        // entity.age becomes entity.tickCount
        if (entity.tickCount % 15 == 0) {

            // entity.damage becomes entity.hurt
            entity.hurt(ModDamageTypes.fracture(entity.level()), 1.5f);

            // flicker
            entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 4, 0, true, false, false));
            if (entity instanceof ServerPlayer p) {
                // TODO: Uncomment once RenderPackets is ported
                // RenderPackets.hidePlayerFromOthers(p, 4);
            }

            // fx
            if (entity.level() instanceof ServerLevel world) {
                DustParticleOptions darkBlue = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f), 1.4f);
                DustParticleOptions midBlue = new DustParticleOptions(new Vector3f(0.2f, 0.4f, 1.0f), 1.2f);
                DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1.0f), 0.9f);

                double x = entity.getX();
                double y = entity.getY(0.5);
                double z = entity.getZ();

                world.sendParticles(brightBlue, x, y, z, 8, 0.4, 0.6, 0.4, 0.02);
                world.sendParticles(midBlue, x, y, z, 5, 0.3, 0.5, 0.3, 0.01);
                world.sendParticles(darkBlue, x, y, z, 2, 0.2, 0.3, 0.2, 0.01);

                // Play random flicker sound (Added .get() because ModSounds are DeferredHolders)
                SoundEvent flickerSound = switch (world.random.nextInt(3)) {
                    case 0 -> ModSounds.FLICKER.get();
                    case 1 -> ModSounds.FLICKER2.get();
                    default -> ModSounds.FLICKER3.get();
                };

                world.playSound(null, entity.blockPosition(), flickerSound, SoundSource.PLAYERS, 0.5f, 1.0f);
            }
        }
        return true;
    }
}