package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.ui.CooldownUI;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import com.loopy.loopypowers.sound.ModSounds;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// HANDLES ALL ATTRIBUTES OF SPEED POWER

public class SpeedPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, SpeedState> ACTIVE_STATES = new HashMap<>();

    private static class SpeedState {
        int burstCdTicks = 0;

        // Primary Dash
        int dashCharges = DASH_MAX_CHARGES;
        int dashRechargeTicks = 0;
        int dashLockTicks = 0;
        int dashActiveTicks = 0;
        Vec3 dashDir = Vec3.ZERO;

        // Secondary Pinball Strike
        boolean isPinballing = false;
        int pinballHitsLeft = 0;
        int pinballTicksLeft = 0;
        Vec3 pinballAnchor = null;
        final Set<UUID> pinballHitTargets = new HashSet<>();
        UUID pinballCurrentTarget = null;

        // Ultimate Overdrive
        int overdriveTicks = 0;
        int overdriveLoopCd = 0;
        int overdriveSlowTicks = 0;
        int blockDmgCd = 0;
    }

    private static SpeedState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new SpeedState());
    }

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
    private static final int    DASH_MAX_CHARGES            = 2;
    private static final int    DASH_RECHARGE_TICKS         = 140;     // 7 seconds per charge
    private static final int    DASH_LOCK_TICKS             = 8;       // small lock between casts
    private static final int    DASH_DURATION_TICKS         = 5;
    private static final double DASH_SPEED                  = 3.2;
    private static final double DASH_MAX_Y                  = 0.45;
    private static final float  DASH_DAMAGE                 = 8.5f;    // Collision damage
    private static final double DASH_KNOCKBACK              = 1.35;
    private static final double DASH_KNOCKBACK_Y            = 0.35;

    // SECONDARY - PINBALL STRIKE
    private static final int    PINBALL_MAX_HITS            = 15;       // Max number of dashes
    private static final double PINBALL_RADIUS              = 12.0;    // Area of effect around the anchor
    private static final float  PINBALL_DAMAGE              = 12.5f;   // Damage per hit
    private static final double PINBALL_KNOCKBACK           = 0.9;     // Knockback per hit
    private static final double PINBALL_KNOCKBACK_Y         = 0.25;

    // ULT - OVERDRIVE
    private static final int    OVERDRIVE_DURATION_TICKS    = 200;     // 10 seconds
    private static final double OVERDRIVE_MIN_SPEED         = 1.4;
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
    private static final int OVERDRIVE_LOOP_INTERVAL_TICKS = 18;

    // particles
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

        player.addEffect(
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, PASSIVE_SPEED_AMPLIFIER, true, false)
        );
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.remove(player.getUUID());

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
        if (state.overdriveLoopCd > 0) state.overdriveLoopCd--;

        tickPassiveSpeed(player);
        tickLowHealthBurst(player, state);

        // --- DASH TICKING ---
        if (state.dashLockTicks > 0) state.dashLockTicks--;
        tickDashRecharge(player, state);
        updateDashCooldownUI(player, state);

        if (state.dashActiveTicks > 0 && !state.isPinballing) {
            state.dashActiveTicks--;
            tickDashSurge(player, state);

            if (state.dashActiveTicks == 0) {
                Vec3 vel = player.getDeltaMovement();
                player.setDeltaMovement(vel.x * 0.15, Math.min(vel.y, 0.0), vel.z * 0.15);
                player.hasImpulse = true;
                player.hurtMarked = true;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }

        // --- PINBALL TICKING ---
        if (state.isPinballing) {
            tickPinballStrike(player, state);
        }

        // --- OVERDRIVE TICKING ---
        if (state.overdriveTicks > 0 && !state.isPinballing) {
            state.overdriveTicks--;
            tickOverdrive(player, state);

            if (state.overdriveLoopCd <= 0) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSHLOOP.get(), player.getSoundSource(), 0.55f, 1.3f);
                state.overdriveLoopCd = OVERDRIVE_LOOP_INTERVAL_TICKS;
            }
        }
    }

    /* ============================================================
       PRIMARY — DASH SURGE
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        SpeedState state = getState(player);

        if (state.isPinballing) return; // Lockout
        if (state.dashLockTicks > 0) return;
        if (state.dashCharges <= 0) return;

        state.dashCharges--;
        state.dashLockTicks = DASH_LOCK_TICKS;
        if (state.dashRechargeTicks <= 0) {
            state.dashRechargeTicks = PowerManager.getModifiedCooldownTicks(player, DASH_RECHARGE_TICKS);
        }

        state.dashActiveTicks = DASH_DURATION_TICKS;
        Vec3 look = player.getViewVector(1.0F);

        double y = look.y * DASH_SPEED;
        if (player.onGround()) {
            if (y > 0) y *= 0.3;
            if (y < 0.2) y = 0.2;
        }
        y = Math.min(y, DASH_MAX_Y);

        state.dashDir = new Vec3(look.x * DASH_SPEED, y, look.z * DASH_SPEED);

        player.setDeltaMovement(state.dashDir);
        player.hasImpulse = true;

        spawnDashCastParticles(player, look);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DASH.get(), player.getSoundSource(), 1.0f, 1.0f);
    }

    private void tickDashRecharge(ServerPlayer player, SpeedState state) {
        if (PowerManager.areCooldownsDisabled()) {
            state.dashCharges = DASH_MAX_CHARGES;
            state.dashRechargeTicks = 0;
            return;
        }

        if (state.dashCharges >= DASH_MAX_CHARGES) {
            state.dashRechargeTicks = 0;
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, DASH_RECHARGE_TICKS);

        if (state.dashRechargeTicks <= 0) {
            state.dashRechargeTicks = maxTicks;
        }

        state.dashRechargeTicks--;

        if (state.dashRechargeTicks <= 0) {
            state.dashCharges++;
            if (state.dashCharges < DASH_MAX_CHARGES) {
                state.dashRechargeTicks = maxTicks;
            } else {
                state.dashRechargeTicks = 0;
            }
        }
    }

    private void updateDashCooldownUI(ServerPlayer player, SpeedState state) {
        String key = "SpeedUI:PRIMARY";

        if (state.dashCharges >= DASH_MAX_CHARGES || PowerManager.areCooldownsDisabled()) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, DASH_RECHARGE_TICKS);
        long endMs = System.currentTimeMillis() + (state.dashRechargeTicks * 50L);

        Component suffix = CooldownUI.makeChargeSuffix(
                state.dashCharges, DASH_MAX_CHARGES, state.dashRechargeTicks, Math.max(1, maxTicks)
        );

        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    private void tickDashSurge(ServerPlayer player, SpeedState state) {
        player.setDeltaMovement(state.dashDir);
        player.hasImpulse = true;
        player.fallDistance = 0;

        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        player.serverLevel()
                .getEntities(player, player.getBoundingBox().inflate(1.2), e -> e instanceof LivingEntity && e.isAlive())
                .forEach(e -> {
                    LivingEntity victim = (LivingEntity) e;

                    if (victim.hurt(player.damageSources().playerAttack(player), DASH_DAMAGE)) {
                        Vec3 pushDir = state.dashDir.normalize();
                        victim.setDeltaMovement(victim.getDeltaMovement().add(
                                pushDir.x * DASH_KNOCKBACK,
                                DASH_KNOCKBACK_Y,
                                pushDir.z * DASH_KNOCKBACK
                        ));
                        victim.hasImpulse = true;

                        if (victim instanceof ServerPlayer sp) {
                            sp.hurtMarked = true;
                            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                        }

                        player.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, player.getSoundSource(), 1.0f, 1.3f);
                        CameraShake.shakeNearby(player, 6.0, 5, 0.25f);
                    }
                });

        spawnDashTrailParticles(player);
    }

    /* ============================================================
       SECONDARY — PINBALL STRIKE (Physical State Machine)
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        SpeedState state = getState(player);

        if (state.isPinballing) return; // Prevent overlapping

        // Initialize State Machine
        state.isPinballing = true;
        state.pinballHitsLeft = PINBALL_MAX_HITS;
        state.pinballCurrentTarget = null;
        state.pinballAnchor = player.position();
        state.pinballHitTargets.clear();

        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.RUSHSTART.get(), player.getSoundSource(), 0.2f, 1.5f);
    }

    private void tickPinballStrike(ServerPlayer player, SpeedState state) {
        ServerLevel w = player.serverLevel();

        // anchor
        if (w.getGameTime() % 3 == 0) {
            for(int i = 0; i < 36; i++) {
                double angle = i * Math.PI * 2 / 36;
                w.sendParticles(WHITE_STREAK,
                        state.pinballAnchor.x + Math.cos(angle) * PINBALL_RADIUS,
                        state.pinballAnchor.y + 0.1,
                        state.pinballAnchor.z + Math.sin(angle) * PINBALL_RADIUS,
                        1, 0, 0, 0, 0);
            }
        }

        // Negate fall damage
        player.fallDistance = 0;

        if (state.pinballCurrentTarget == null) {
            // Scan for alive entities within the anchor radius, requiring strictly explicit Line of Sight
            List<LivingEntity> allTargets = w.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(PINBALL_RADIUS).move(state.pinballAnchor.subtract(player.position())),
                    e -> e.isAlive() && e != player
                            && e.position().distanceToSqr(state.pinballAnchor) <= (PINBALL_RADIUS * PINBALL_RADIUS)
                            && player.hasLineOfSight(e));

            if (allTargets.isEmpty() || state.pinballHitsLeft <= 0) {
                endPinballStrike(player, state);
                return;
            }

            List<LivingEntity> unhitTargets = allTargets.stream()
                    .filter(e -> !state.pinballHitTargets.contains(e.getUUID()))
                    .toList();

            LivingEntity target;
            if (!unhitTargets.isEmpty()) {
                target = unhitTargets.get(w.random.nextInt(unhitTargets.size()));
            } else {
                // break after same entity hit twice
                target = allTargets.get(w.random.nextInt(allTargets.size()));
                state.pinballHitsLeft = 1;
            }

            state.pinballHitsLeft--;
            state.pinballHitTargets.add(target.getUUID());
            state.pinballCurrentTarget = target.getUUID();

            forceLookAt(player, target.getEyePosition());

            // Calculate Launch Velocity
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() / 2.0, 0);
            Vec3 playerCenter = player.position().add(0, player.getBbHeight() / 2.0, 0);
            Vec3 dir = targetCenter.subtract(playerCenter);
            double dist = dir.length();

            // Cap the physical speed to ensure we don't clip through thick walls instantly
            double speed = Math.max(2.5, Math.min(dist / 2.0, 5.0));
            Vec3 dashVel = dir.normalize().scale(speed);

            player.setDeltaMovement(dashVel);
            player.hasImpulse = true;
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));

            w.playSound(null, player.blockPosition(), ModSounds.DARKNESSTELEPORT2.get(), player.getSoundSource(), 1.0f, 1.2f);
            spawnDashCastParticles(player, dir.normalize());

            // Dynamically set travel timeout based on distance and speed (Max ~10 ticks)
            state.pinballTicksLeft = Math.min(10, (int) Math.ceil(dist / speed) + 1);

        } else {
            // travel and hit
            state.pinballTicksLeft--;

            // Maintain some vertical float so they don't drop
            Vec3 vel = player.getDeltaMovement();
            if (vel.y < 0 && !player.onGround()) {
                player.setDeltaMovement(vel.x, vel.y * 0.4, vel.z);
                player.hasImpulse = true;
            }

            spawnDashTrailParticles(player);

            Entity targetEnt = w.getEntity(state.pinballCurrentTarget);

            // If target is dead/gone, or we reached max ticks, or we are physically close enough do the hit
            boolean close = targetEnt != null && player.distanceToSqr(targetEnt) < 16.0;

            if (state.pinballTicksLeft <= 0 || close) {

                if (targetEnt instanceof LivingEntity target && target.isAlive() && close) {
                    player.swing(InteractionHand.MAIN_HAND, true);

                    if (target.hurt(ModDamageTypes.rush(w, player), PINBALL_DAMAGE)) {
                        Vec3 push = target.position().subtract(player.position()).normalize();
                        target.setDeltaMovement(push.x * PINBALL_KNOCKBACK, PINBALL_KNOCKBACK_Y, push.z * PINBALL_KNOCKBACK);
                        target.hasImpulse = true;

                        if (target instanceof ServerPlayer sp) {
                            sp.hurtMarked = true;
                            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                        }

                        CameraShake.shakeNearby(player, 6.0, 5, 0.4f);
                    }

                    w.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0f, 1.4f);
                    w.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 0.8f, 1.1f);
                }

                // Stop at the target
                player.setDeltaMovement(0, 0, 0);
                player.hasImpulse = true;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));

                // Clear target so the next tick instantly begins the finding/launching phase
                state.pinballCurrentTarget = null;
            }
        }
    }

    private void endPinballStrike(ServerPlayer player, SpeedState state) {
        state.isPinballing = false;
        state.pinballHitTargets.clear();
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 1.0f, 0.8f);
    }

    /* ============================================================
       ULTIMATE — OVERDRIVE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        SpeedState state = getState(player);

        if (state.isPinballing) return; // Lockout

        state.overdriveTicks = OVERDRIVE_DURATION_TICKS;
        state.overdriveLoopCd = 0;

        // Ensure we always have a valid flat directional vector even if looking straight up/down
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        if (flatLook.lengthSqr() < 1.0e-6) {
            flatLook = Vec3.directionFromRotation(0, player.getYRot());
            flatLook = new Vec3(flatLook.x, 0, flatLook.z);
        }
        flatLook = flatLook.normalize();

        player.setDeltaMovement(
                flatLook.x * (OVERDRIVE_MIN_SPEED * 1.8),
                player.getDeltaMovement().y + 0.2,
                flatLook.z * (OVERDRIVE_MIN_SPEED * 1.8)
        );
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

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

        // AUTHORITATIVE SYNC: Ensures the NeoForge client cannot use WASD to fight the forced forward velocity
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /* ============================================================
       PARTICLE HELPERS
       ============================================================ */

    private static void spawnDashCastParticles(ServerPlayer player, Vec3 look) {
        ServerLevel world = player.serverLevel();
        double ox = player.getX();
        double oy = player.getY() + 0.8;
        double oz = player.getZ();

        world.sendParticles(
                WHITE_BRIGHT, ox, oy, oz,
                12, 0.3, 0.25, 0.3, 0.06
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

    private static void spawnDashTrailParticles(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 vel = player.getDeltaMovement();
        if (vel.lengthSqr() < 0.1) return;
        Vec3 back = vel.normalize().scale(-1);

        world.sendParticles(
                WHITE_PALE,
                player.getX() + back.x * 0.5,
                player.getY() + 0.8 + back.y * 0.5,
                player.getZ() + back.z * 0.5,
                6, 0.3, 0.4, 0.3, 0.02
        );

        for (int i = 0; i < 5; i++) {
            double ox = (world.random.nextDouble() - 0.5) * 1.5;
            double oy = (world.random.nextDouble() - 0.5) * 1.5;
            double oz = (world.random.nextDouble() - 0.5) * 1.5;
            world.sendParticles(
                    WHITE_STREAK,
                    player.getX() + ox,
                    player.getY() + 0.8 + oy,
                    player.getZ() + oz,
                    0,
                    back.x * 0.6,
                    back.y * 0.6,
                    back.z * 0.6,
                    1.0
            );
        }

        if (world.getGameTime() % 2 == 0) {
            world.sendParticles(
                    ParticleTypes.SWEEP_ATTACK,
                    player.getX() + back.x,
                    player.getY() + 0.5,
                    player.getZ() + back.z,
                    1, 0.2, 0.2, 0.2, 0
            );
        }
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
       BEHAVIOUR HELPERS
       ============================================================ */

    private static void forceLookAt(ServerPlayer player, Vec3 targetPos) {
        Vec3 dir = targetPos.subtract(player.getEyePosition());
        if (dir.lengthSqr() < 0.0001) return;

        float  targetYaw   = (float)(Math.toDegrees(Mth.atan2(dir.z, dir.x))) - 90f;
        double horizontal  = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float  targetPitch = (float)(-Math.toDegrees(Mth.atan2(dir.y, horizontal)));

        float yawDiff   = Mth.wrapDegrees(targetYaw - player.getYRot());
        float pitchDiff = targetPitch - player.getXRot();

        player.connection.send(new ClientboundPlayerPositionPacket(
                0, 0, 0,
                yawDiff,
                pitchDiff,
                java.util.EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z, RelativeMovement.Y_ROT, RelativeMovement.X_ROT),
                0
        ));
    }

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

        CameraShake.shakeNearby(player, 10.0, 5, 15);
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

        CameraShake.shakeNearby(player, 10.0, 6, 17);
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
        Vec3 look = player.getViewVector(1.0F);
        Vec3 currentVel = player.getDeltaMovement();

        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        if (flatLook.lengthSqr() < 1.0e-6) {
            flatLook = Vec3.directionFromRotation(0, player.getYRot());
            flatLook = new Vec3(flatLook.x, 0, flatLook.z);
        }

        double slowFactor = (state.overdriveSlowTicks > 0)
                ? Mth.clamp(state.overdriveSlowTicks / 15.0, 0.15, 1.0)
                : 1.0;

        Vec3 targetHoriz = flatLook.normalize().scale(OVERDRIVE_MIN_SPEED * slowFactor);

        // Completely overriding the current X/Z with the newly calculated forward momentum
        player.setDeltaMovement(targetHoriz.x, currentVel.y, targetHoriz.z);
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
    public long getPrimaryCooldownMs() { return 0; } // Controlled completely by charges now

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