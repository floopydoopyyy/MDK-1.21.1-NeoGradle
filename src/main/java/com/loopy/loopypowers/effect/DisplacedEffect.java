package com.loopy.loopypowers.effect;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Set;

public class DisplacedEffect extends MobEffect {

    public DisplacedEffect() {
        super(MobEffectCategory.HARMFUL, 0x00FFFF); // cyan
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // Apply every tick
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        ServerLevel level = (ServerLevel) entity.level();

        // set velocity to 0
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true; // replaces velocityModified
        entity.fallDistance = 0; // stops fall damage accumulating when frozen

        // Lock position using packets (no more camera lock)
        if (entity instanceof ServerPlayer displacedPlayer) {
            displacedPlayer.connection.send(
                    new ClientboundPlayerPositionPacket(
                            displacedPlayer.getX(),
                            displacedPlayer.getY(),
                            displacedPlayer.getZ(),
                            0f, // 0 with relative flags means no camera snapping!
                            0f,
                            Set.of(
                                    RelativeMovement.X_ROT,
                                    RelativeMovement.Y_ROT
                            ),
                            0
                    )
            );

            // stop mining
            displacedPlayer.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, 5, 255, true, false, false));

            // stop actions
            if (!displacedPlayer.getMainHandItem().isEmpty()) {
                displacedPlayer.getCooldowns().addCooldown(displacedPlayer.getMainHandItem().getItem(), 5);
            }
            displacedPlayer.stopUsingItem();
        }

        // invisibility
        entity.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, 5, 0, true, false, false));

        // stop damage (Resistance is now DAMAGE_RESISTANCE)
        entity.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 5, 255, true, false, false));
        entity.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, 5, 255, true, false, false));

        // disable mob ai
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }

        // fx
        DustParticleOptions darkBlue  = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f),  1.4f);
        DustParticleOptions midBlue   = new DustParticleOptions(new Vector3f(0.2f,  0.4f, 1.0f),  1.2f);
        DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1.0f),  0.9f);

        double x = entity.getX();
        double y = entity.getY(0.5);
        double z = entity.getZ();

        level.sendParticles(brightBlue,  x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
        level.sendParticles(midBlue,     x, y, z, 4, 0.35, 0.55, 0.35, 0.02);

        if (level.random.nextFloat() < 0.4f) {
            level.sendParticles(darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        }

        return true;
    }
}