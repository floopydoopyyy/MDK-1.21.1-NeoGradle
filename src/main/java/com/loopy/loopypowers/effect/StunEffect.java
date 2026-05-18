package com.loopy.loopypowers.effect;

// import com.loopy.loopypowers.network.CameraShake;
// import com.loopy.loopypowers.network.RenderPackets;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.RenderPackets;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

public class StunEffect extends MobEffect {

    // Bright yellow dust for the "cartoon stars" effect
    private static final DustParticleOptions STUN_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.1f), 1.0f);

    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0xF7CB15); // grey yellow

        // reduce movement speed
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath("loopypowers", "stun_slow"),
                -0.80d,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );

        // reduce weapon swing speed
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ResourceLocation.fromNamespaceAndPath("loopypowers", "stun_attack_speed"),
                -0.50d,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );

        // reduce attack damage
        this.addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                ResourceLocation.fromNamespaceAndPath("loopypowers", "stun_damage"),
                -4.0d,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        super.onEffectAdded(entity, amplifier);

        // Play the ringing sound exactly when effect applied
        if (!entity.level().isClientSide && entity instanceof ServerPlayer player) {
            // Route sound through the world
            player.level().playSound(null, player.blockPosition(), ModSounds.EARRING.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // Forces the effect to tick every single frame
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;

        // STARS
        if (entity.level() instanceof ServerLevel world) {
            double radius = 0.55; // Distance from the center of the head
            double height = entity.getY() + entity.getBbHeight() + 0.35; // Just above the head

            // Use the entity's age (tickCount) to rotate the particles
            double angle1 = (entity.tickCount * 0.25) % (2 * Math.PI); // 0.25 controls the rotation speed
            double angle2 = angle1 + Math.PI; // Offset the second star by 180 degrees

            double x1 = entity.getX() + Math.cos(angle1) * radius;
            double z1 = entity.getZ() + Math.sin(angle1) * radius;

            double x2 = entity.getX() + Math.cos(angle2) * radius;
            double z2 = entity.getZ() + Math.sin(angle2) * radius;

            // Spawn the particles with 0 velocity so they form a orbit
            world.sendParticles(STUN_DUST, x1, height, z1, 1, 0, 0, 0, 0);
            world.sendParticles(STUN_DUST, x2, height, z2, 1, 0, 0, 0, 0);
        }

        if (entity instanceof ServerPlayer p) {
            // Player Stun Logic
            if (p.tickCount % 10 == 0) { // throttles audio packets

                int duration = 0;
                // getStatusEffects becomes getActiveEffects
                for (MobEffectInstance instance : p.getActiveEffects()) {
                    if (instance.getEffect().value() == this) {
                        duration = instance.getDuration();
                        break;
                    }
                }

                if (duration > 0) {
                    RenderPackets.sendStunAudio(p, duration);
                }
            }

            p.setSprinting(false);
            p.fallDistance = 0.0f; // prevents fall damage accumulating
            CameraShake.shake(p, 2, 1.9f);

        } else {
            // Mob Stun Logic
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
            }

            // Lock look direction (prevYaw/prevPitch -> yRotO/xRotO)
            entity.setYRot(entity.yRotO);
            entity.setXRot(entity.xRotO);
        }

        return true;
    }
}