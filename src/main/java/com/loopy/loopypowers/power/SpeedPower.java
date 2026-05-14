package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import com.loopy.loopypowers.sound.ModSounds;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// HANDLES ALL ATTRIBUTES OF SPEED POWER

public class SpeedPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, SpeedState> ACTIVE_STATES = new HashMap<>();

    private static class SpeedState {
        int burstCdTicks = 0;
        int rushTicks = 0;
        int rushLoopCd = 0;
        int overdriveTicks = 0;
        int overdriveLoopCd = 0;
        int overdriveSlowTicks = 0;
        int blockDmgCd = 0;
    }

    private static SpeedState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new SpeedState());
    }

    // NEW HELPER GETTER FOR MIXINS
    public static boolean isOverdriveActive(ServerPlayer player) {
        SpeedState state = ACTIVE_STATES.get(player.getUUID());
        return state != null && state.overdriveTicks > 0;
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // PASSIVE
    private static final int    PASSIVE_SPEED_AMPLIFIER     = 1;       // swiftness 2
    private static final float  PASSIVE_LOW_HEALTH_THRESHOLD = 4.0f;   // 3 hearts
    private static final int    PASSIVE_BURST_AMPLIFIER     = 4;       // swiftness 5 burst
    private static final int    PASSIVE_BURST_DURATION      = 80;      // 4 seconds
    private static final int    PASSIVE_BURST_COOLDOWN_TICKS = 500;    // 10 second cooldown

    // PRIMARY
    private static final double DASH_HORIZONTAL_STRENGTH    = 2.6;
    private static final double DASH_VERTICAL_STRENGTH      = 1.3;
    private static final double DASH_MAX_DOWN               = -0.8;
    private static final double DASH_MAX_UP                 = 0.8;
    private static final double DASH_GROUND_MIN_Y           = 0.18;
    private static final int    DASH_PARTICLE_COUNT         = 12;

    // SECONDARY
    private static final int    RUSH_DURATION_TICKS         = 100;     // 5 seconds
    private static final float  RUSH_DAMAGE                 = 12.5f;
    private static final double RUSH_KNOCKBACK_HORIZONTAL   = 1.4;
    private static final double RUSH_KNOCKBACK_VERTICAL     = 0.55;
    private static final double RUSH_KNOCKBACK_SCAN_RADIUS  = 4.5;
    private static final double RUSH_VELOCITY_BONUS         = 1.5;     // flat horizontal velocity on cast
    private static final int    RUSH_HASTE_AMPLIFIER        = 0;

    // ULT
    private static final int    OVERDRIVE_DURATION_TICKS    = 200;     // 10 seconds
    private static final double OVERDRIVE_MIN_SPEED         = 1.4;
    private static final double OVERDRIVE_FORWARD_PUSH      = 0.38;
    private static final double OVERDRIVE_ENTITY_DAMAGE     = 19.5f;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK  = 1.8;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK_Y = 0.5;
    private static final double OVERDRIVE_COLLISION_SLOW_X  = 0.15;
    private static final double OVERDRIVE_COLLISION_SLOW_Y  = 0.10;
    private static final int    OVERDRIVE_SLOW_TICKS        = 12;
    private static final float  OVERDRIVE_BLOCK_HARDNESS_MAX = 3.0f;
    private static final float  OVERDRIVE_SELF_DAMAGE       = 1.5f;
    private static final int    OVERDRIVE_JUMP_AMPLIFIER    = 0;
    private static final int    OVERDRIVE_HASTE_AMPLIFIER   = 2;

    // EASTER EGG
    private static final int    A_TRAIN_CHANCE   = 250;

    // sound
    private static final int RUSH_LOOP_INTERVAL_TICKS      = 25;
    private static final int OVERDRIVE_LOOP_INTERVAL_TICKS      = 18;

    // particles (Refactored to White)
    private static final DustParticleOptions WHITE_BRIGHT  =
            new DustParticleOptions(new Vector3f(1.00f, 1.00f, 1.00f), 1.3f);
    private static final DustParticleOptions WHITE_PALE    =
            new DustParticleOptions(new Vector3f(0.85f, 0.85f, 0.85f), 1.0f);
    private static final DustParticleOptions WHITE_STREAK =
            new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.95f), 0.8f);

    /* ============================================================
       PASSIVE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.put(player.getUUID(), new SpeedState());

        // passive
        player.addEffect(
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, PASSIVE_SPEED_AMPLIFIER, true, false)
        );
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.remove(player.getUUID());

        // Strip lingering buffs
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DIG_SPEED);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        SpeedState state = getState(player);

        // Tick internal cooldowns
        if (state.burstCdTicks > 0) state.burstCdTicks--;
        if (state.blockDmgCd > 0) state.blockDmgCd--;
        if (state.overdriveSlowTicks > 0) state.overdriveSlowTicks--;
        if (state.rushLoopCd > 0) state.rushLoopCd--;
        if (state.overdriveLoopCd > 0) state.overdriveLoopCd--;

        tickPassiveSpeed(player);
        tickLowHealthBurst(player, state);

        if (state.rushTicks > 0) {
            state.rushTicks--;
            tickRushEffects(player);

            // Loop sound
            if (state.rushLoopCd <= 0) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSHLOOP.get(), player.getSoundSource(), 0.55f, 1.0f);
                state.rushLoopCd = RUSH_LOOP_INTERVAL_TICKS;
            }
        }

        if (state.overdriveTicks > 0) {
            state.overdriveTicks--;
            tickOverdrive(player, state);

            // Ultimate Loop Sound
            if (state.overdriveLoopCd <= 0) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSHLOOP.get(), player.getSoundSource(), 0.55f, 1.3f);
                state.overdriveLoopCd = OVERDRIVE_LOOP_INTERVAL_TICKS;
            }
        }
    }

    /* ============================================================
       PRIMARY — DASH
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);

        double y = Mth.clamp(
                look.y * DASH_VERTICAL_STRENGTH,
                DASH_MAX_DOWN,
                DASH_MAX_UP
        );
        double x = look.x * DASH_HORIZONTAL_STRENGTH;
        double z = look.z * DASH_HORIZONTAL_STRENGTH;

        if (player.onGround()) {
            y = Math.max(y, DASH_GROUND_MIN_Y);
        }

        player.setDeltaMovement(player.getDeltaMovement().add(x, y, z));
        player.hasImpulse = true;

        spawnDashParticles(player, look);

        player.serverLevel().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.DASH.get(),
                player.getSoundSource(),
                1.0f, 1.0f
        );
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0f, 1.2f);
    }

    /* ============================================================
       SECONDARY — RUSH
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        SpeedState state = getState(player);
        state.rushTicks = RUSH_DURATION_TICKS;
        state.rushLoopCd = 0;

        Vec3 look = player.getViewVector(1.0F);
        player.setDeltaMovement(
                look.x * RUSH_VELOCITY_BONUS,
                player.getDeltaMovement().y,
                look.z * RUSH_VELOCITY_BONUS
        );
        player.hasImpulse = true;

        player.addEffect(
                new MobEffectInstance(MobEffects.DIG_SPEED, RUSH_DURATION_TICKS, RUSH_HASTE_AMPLIFIER, true, false)
        );

        // Pushback + damage
        player.serverLevel()
                .getEntities(player, player.getBoundingBox().inflate(RUSH_KNOCKBACK_SCAN_RADIUS),
                        e -> e instanceof LivingEntity && e.isAlive())
                .forEach(e -> {
                    LivingEntity victim = (LivingEntity) e;

                    DamageSource rushSrc = ModDamageTypes.rush(player.level(), player);

                    if (victim.hurt(rushSrc, RUSH_DAMAGE)) {
                        double dx = victim.getX() - player.getX();
                        double dz = victim.getZ() - player.getZ();
                        double dist = Math.sqrt(dx * dx + dz * dz);

                        if (dist > 0.001) {
                            victim.setDeltaMovement(victim.getDeltaMovement().add(
                                    (dx / dist) * RUSH_KNOCKBACK_HORIZONTAL,
                                    RUSH_KNOCKBACK_VERTICAL,
                                    (dz / dist) * RUSH_KNOCKBACK_HORIZONTAL
                            ));
                            victim.hasImpulse = true;
                        }

                        CameraShake.shakeNearby(player, 8.0, 5, 0.45f);
                    }
                });

        spawnRushCastParticles(player);

        // sounds
        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(), 0.3f, 1.2f
        );
        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                ModSounds.RUSHSTART.get(),
                player.getSoundSource(), 0.5f, 1.0f
        );
    }

    /* ============================================================
       ULTIMATE — OVERDRIVE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        SpeedState state = getState(player);
        state.overdriveTicks = OVERDRIVE_DURATION_TICKS;
        state.overdriveLoopCd = 0;

        Vec3 look = player.getViewVector(1.0F);
        player.setDeltaMovement(
                look.x * (OVERDRIVE_MIN_SPEED * 1.8),
                player.getDeltaMovement().y + 0.2,
                look.z * (OVERDRIVE_MIN_SPEED * 1.8)
        );
        player.hasImpulse = true;

        spawnOverdriveCastParticles(player);

        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, player.getSoundSource(), 0.6f, 1.8f);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.OVERDRIVESTART.get(), player.getSoundSource(), 0.2f, 1.5f);
        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(), 1.0f, 0.6f
        );
    }

    /* ============================================================
       TICK HELPERS
       ============================================================ */

    private void tickPassiveSpeed(ServerPlayer player) {
        if (!PassiveManager.isEnabled(player)) return;

        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed == null || speed.getDuration() < 5) {
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, PASSIVE_SPEED_AMPLIFIER, true, false)
            );
        }
    }

    private void tickLowHealthBurst(ServerPlayer player, SpeedState state) {
        if (state.burstCdTicks > 0) return;

        if (player.getHealth() <= PASSIVE_LOW_HEALTH_THRESHOLD) {
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PASSIVE_BURST_DURATION,
                            PASSIVE_BURST_AMPLIFIER, true, false)
            );

            state.burstCdTicks = PASSIVE_BURST_COOLDOWN_TICKS;

            player.serverLevel().sendParticles(
                    WHITE_BRIGHT,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    8, 0.3, 0.4, 0.3, 0.06
            );
            player.serverLevel().sendParticles(
                    ParticleTypes.SWEEP_ATTACK,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.4, 0.2, 0.4, 0
            );
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 0.7f, 1.6f);
        }
    }

    private void tickRushEffects(ServerPlayer player) {
        Vec3 vel   = player.getDeltaMovement();
        Vec3 horiz = new Vec3(vel.x, 0, vel.z);
        if (horiz.length() < RUSH_VELOCITY_BONUS * 0.6) {
            Vec3 look = player.getViewVector(1.0F);
            player.setDeltaMovement(player.getDeltaMovement().add(look.x * 0.18, 0, look.z * 0.18));
            player.hasImpulse = true;
        }

        player.serverLevel().sendParticles(
                ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.05, player.getZ(),
                3, 0.15, 0.05, 0.15, 0.005
        );
    }

    private static void tickOverdrive(ServerPlayer player, SpeedState state) {
        forceForward(player, state);
        breakBlocks(player, state);

        player.addEffect(new MobEffectInstance(
                MobEffects.JUMP, 20, OVERDRIVE_JUMP_AMPLIFIER, true, false));
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SPEED, 20, OVERDRIVE_HASTE_AMPLIFIER, true, false));

        if (player.isInWater()) {
            Vec3 vel = player.getDeltaMovement();
            if (vel.y < 0) {
                player.setDeltaMovement(vel.x, 0.08, vel.z);
                player.hasImpulse = true;
            }
            player.setOnGround(true);
        }

        player.serverLevel()
                .getEntities(player, player.getBoundingBox().inflate(1.2),
                        e -> e instanceof LivingEntity && e.isAlive())
                .forEach(entity -> {
                    DamageSource overdriveSrc = ModDamageTypes.overdrive(player.level(), player);

                    if (entity.hurt(overdriveSrc, (float) OVERDRIVE_ENTITY_DAMAGE)) {

                        // EASTER EGG
                        if (!entity.isAlive() && entity instanceof ServerPlayer) {
                            if (player.getRandom().nextInt(A_TRAIN_CHANCE) == 0) {
                                if (player.getServer() != null) {
                                    Component message = Component.literal(
                                            "<" + player.getName().getString() + "> I can't stop. I can't stop. I can't stop. I can't stop."
                                    );
                                    player.getServer().getPlayerList().broadcastSystemMessage(message, false);
                                }
                            }
                        }

                        Vec3 dir = entity.position().subtract(player.position()).normalize();
                        entity.setDeltaMovement(entity.getDeltaMovement().add(
                                dir.x * OVERDRIVE_ENTITY_KNOCKBACK,
                                OVERDRIVE_ENTITY_KNOCKBACK_Y,
                                dir.z * OVERDRIVE_ENTITY_KNOCKBACK
                        ));
                        entity.hasImpulse = true;

                        applyCollisionSlow(player, state);

                        player.serverLevel().sendParticles(
                                ParticleTypes.EXPLOSION_EMITTER,
                                player.getX(), player.getY() + 1, player.getZ(),
                                4, 0.6, 0.2, 0.6, 0.1
                        );
                        player.serverLevel().playSound(
                                null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.GENERIC_EXPLODE.value(),
                                player.getSoundSource(), 1.0f, 0.8f
                        );
                    }
                });
        spawnOverdriveTrailParticles(player);
    }

    /* ============================================================
       PARTICLE HELPERS
       ============================================================ */

    private static void spawnDashParticles(ServerPlayer player, Vec3 look) {
        ServerLevel world = player.serverLevel();
        double ox = player.getX();
        double oy = player.getY() + 0.8;
        double oz = player.getZ();

        world.sendParticles(
                WHITE_BRIGHT, ox, oy, oz,
                DASH_PARTICLE_COUNT, 0.3, 0.25, 0.3, 0.06
        );

        for (int i = 1; i <= 4; i++) {
            double d   = i * 0.70;
            double vel = 0.04 + i * 0.016;
            world.sendParticles(
                    (i % 2 == 0) ? WHITE_BRIGHT : WHITE_STREAK,
                    ox - look.x * d, oy - look.y * d * 0.5, oz - look.z * d,
                    1, -look.x * vel, 0.01, -look.z * vel, 0.0
            );
        }

        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI * 2.0 / 6;
            world.sendParticles(
                    WHITE_STREAK,
                    ox + Math.cos(angle) * 0.4, oy, oz + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.10, 0.015, Math.sin(angle) * 0.10, 0.0
            );
        }

        world.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                ox, player.getY() + 0.3, oz,
                3, 0.5, 0.15, 0.5, 0
        );
    }

    private static void spawnRushCastParticles(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        double ox = player.getX();
        double oy = player.getY() + 1.0;
        double oz = player.getZ();

        int ringPoints = 12;
        for (int i = 0; i < ringPoints; i++) {
            double angle = i * Math.PI * 2.0 / ringPoints;
            double speed = 0.22;
            world.sendParticles(
                    WHITE_BRIGHT,
                    ox + Math.cos(angle) * 0.5, player.getY() + 0.1, oz + Math.sin(angle) * 0.5,
                    1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0
            );
        }

        world.sendParticles(
                WHITE_BRIGHT, ox, oy, oz,
                10, 0.5, 0.5, 0.5, 0.10
        );
        world.sendParticles(
                WHITE_PALE, ox, oy, oz,
                6, 0.6, 0.6, 0.6, 0.08
        );

        world.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                ox, oy, oz, 3, 0.6, 0.2, 0.6, 0.1
        );
        world.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                ox, player.getY() + 0.5, oz, 4, 0.6, 0.25, 0.6, 0
        );
    }

    private static void spawnOverdriveCastParticles(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        double ox = player.getX();
        double oy = player.getY() + 1.0;
        double oz = player.getZ();

        for (int d = 0; d < 12; d++) {
            double theta = d * Math.PI * 2.0 / 12;
            double phi   = Math.PI / 4;
            double speed = 0.20;
            world.sendParticles(
                    WHITE_BRIGHT, ox, oy, oz,
                    1,
                    Math.cos(theta) * Math.cos(phi) * speed,
                    Math.sin(phi) * speed,
                    Math.sin(theta) * Math.cos(phi) * speed,
                    0.0
            );
        }

        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI * 2.0 / 10;
            world.sendParticles(
                    WHITE_STREAK,
                    ox + Math.cos(angle) * 0.4, player.getY() + 0.1, oz + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.28, 0.01, Math.sin(angle) * 0.28, 0.0
            );
        }

        world.sendParticles(
                ParticleTypes.FIREWORK,
                ox, oy, oz, 8, 0.5, 0.6, 0.5, 0.08
        );
        world.sendParticles(
                ParticleTypes.FLASH,
                ox, oy, oz, 1, 0.4, 0.2, 0.4, 0
        );
        world.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                ox, oy, oz, 5, 0.7, 0.3, 0.7, 0
        );
    }


    private static void spawnOverdriveTrailParticles(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 vel = player.getDeltaMovement();
        double ox  = player.getX();
        double oy  = player.getY() + 0.8;
        double oz  = player.getZ();

        Vec3 back = vel.normalize().scale(-1);
        for (int i = 0; i < 3; i++) {
            double d       = 0.5 + i * 1.1;
            double spread  = 0.10 + i * 0.08;
            double speed   = 0.06 + i * 0.024;
            world.sendParticles(
                    (i % 2 == 0) ? WHITE_BRIGHT : WHITE_PALE,
                    ox + back.x * d, oy + back.y * d * 0.3, oz + back.z * d,
                    1, back.x * speed + spread, 0.03, back.z * speed + spread, 0.0
            );
        }

        world.sendParticles(
                ParticleTypes.CLOUD,
                ox + back.x * 1.5, player.getY() + 0.5, oz + back.z * 1.5,
                3, 0.25, 0.20, 0.25, 0.04
        );
        world.sendParticles(
                ParticleTypes.SMOKE,
                ox + back.x, player.getY() + 0.7, oz + back.z,
                2, 0.15, 0.15, 0.15, 0.03
        );

        world.sendParticles(
                ParticleTypes.END_ROD,
                ox, oy, oz,
                4, 0.3, 0.6, 0.3, 0.06
        );
        world.sendParticles(
                ParticleTypes.FIREWORK,
                ox, oy, oz,
                2, 0.35, 0.55, 0.25, 0.05
        );

        if ((int)(player.level().getGameTime() % 4) == 0) {
            Vec3 right = new Vec3(-vel.z, 0, vel.x).normalize();
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI * 2.0 / 4;
                double rx    = right.x * Math.cos(angle) * 0.6;
                double rz    = right.z * Math.cos(angle) * 0.6;
                world.sendParticles(
                        WHITE_STREAK,
                        ox + rx, oy, oz + rz,
                        1, back.x * 0.12 + rx * 0.06, 0.015, back.z * 0.12 + rz * 0.06, 0.0
                );
            }
        }
    }

    /* ============================================================
       OVERDRIVE BEHAVIOUR HELPERS
       ============================================================ */

    private static void applyCollisionSlow(ServerPlayer player, SpeedState state) {
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X
        );
        player.hasImpulse = true;

        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 5, 2, true, false
        ));

        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SMALL_FALL,
                player.getSoundSource(), 0.8f, 0.7f
        );

        CameraShake.shakeNearby(player, 10.0, 5, 15); // Adjust distance if needed
    }

    private static void applyCollisionSlowBlock(ServerPlayer player, SpeedState state) {
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X
        );
        player.hasImpulse = true;

        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 8, 2, true, false
        ));

        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                player.getSoundSource(), 0.8f, 0.7f
        );

        CameraShake.shakeNearby(player, 10.0, 6, 17); // Adjust distance if needed
    }

    private static void breakBlocks(ServerPlayer player, SpeedState state) {
        if (state.overdriveSlowTicks > 0) return;

        ServerLevel world   = player.serverLevel();
        BlockPos base = player.blockPosition();
        boolean impactedThisTick = false;

        for (BlockPos pos : BlockPos.betweenClosed(
                base.offset(-1, 0, -1),
                base.offset(1, 2, 1)
        )) {
            var blockState = world.getBlockState(pos);
            if (blockState.isAir() || blockState.is(BlockTags.FIRE)) continue;

            float hardness = blockState.getDestroySpeed(world, pos);

            if (hardness < 0) {
                if (!impactedThisTick) {
                    handleBlockImpact(player, state, 2.0f);
                    impactedThisTick = true;
                }
                continue;
            }

            if (blockState.canBeReplaced()
                    || blockState.is(BlockTags.LEAVES)
                    || blockState.is(BlockTags.FLOWERS)
                    || blockState.is(BlockTags.SMALL_FLOWERS)
                    || blockState.is(BlockTags.TALL_FLOWERS)) {
                world.destroyBlock(pos, false);
                continue;
            }

            if (hardness <= OVERDRIVE_BLOCK_HARDNESS_MAX && !blockState.canBeReplaced()) {
                world.destroyBlock(pos, true, player);

                if (!impactedThisTick) {
                    handleBlockImpact(player, state, 1.4f);
                    impactedThisTick = true;
                }
            }
        }
    }

    private static void handleBlockImpact(ServerPlayer player, SpeedState state, float shakeStrength) {
        if (!player.horizontalCollision) return;

        if (state.overdriveSlowTicks > 0) return;

        applyCollisionSlowBlock(player, state);
        applyCollisionSelfDamage(player, state);

        CameraShake.shakeNearby(player, 12.0, 8, shakeStrength);

        player.serverLevel().sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1, player.getZ(),
                3, 0.15, 0.10, 0.15, 0.02
        );
        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE,
                player.getSoundSource(), 0.8f, 0.85f
        );
    }

    private static void forceForward(ServerPlayer player, SpeedState state) {
        Vec3 look   = player.getViewVector(1.0F);
        Vec3 vel    = player.getDeltaMovement();
        Vec3 horiz  = new Vec3(vel.x, 0, vel.z);

        if (horiz.length() >= OVERDRIVE_MIN_SPEED) return;

        double slowFactor = (state.overdriveSlowTicks > 0)
                ? Mth.clamp(state.overdriveSlowTicks / 15.0, 0.15, 1.0)
                : 1.0;

        Vec3 push = new Vec3(look.x, 0, look.z)
                .normalize()
                .scale(OVERDRIVE_FORWARD_PUSH * slowFactor);

        player.setDeltaMovement(player.getDeltaMovement().add(push.x, 0, push.z));
        player.hasImpulse = true;
    }

    private static void applyCollisionSelfDamage(ServerPlayer player, SpeedState state) {
        if (state.blockDmgCd > 0) return;

        player.hurt(ModDamageTypes.wallCollision(player.level()), OVERDRIVE_SELF_DAMAGE);

        state.blockDmgCd = 10;

        player.serverLevel().playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_HURT,
                player.getSoundSource(), 0.8f, 1.0f
        );
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override
    public String getName() { return Component.translatable("power.loopypowers.speed.name").getString(); }

    @Override
    public String getPassiveName() { return Component.translatable("power.loopypowers.speed.passive_name").getString(); }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.speed.description.passive").getString();
    }

    @Override
    public String getPrimaryName() { return Component.translatable("power.loopypowers.speed.primary_name").getString(); }

    @Override
    public long getPrimaryCooldownMs() { return 6_000; }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.speed.description.primary").getString();
    }

    @Override
    public String getSecondaryName() { return Component.translatable("power.loopypowers.speed.secondary_name").getString(); }

    @Override
    public long getSecondaryCooldownMs() { return 18_000; }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.speed.description.secondary").getString();
    }

    @Override
    public String getUltimateName() { return Component.translatable("power.loopypowers.speed.ultimate_name").getString(); }

    @Override
    public long getUltimateCooldownMs() { return 400_000; }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.speed.description.ultimate").getString();
    }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.speed.description.overview").getString();
    }
}