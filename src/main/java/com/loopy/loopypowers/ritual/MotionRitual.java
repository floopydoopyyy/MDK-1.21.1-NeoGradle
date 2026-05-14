package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.*;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.List;
import java.util.function.Supplier;

public class MotionRitual implements RitualInterface {

    // POWERS

    private static final List<Supplier<PowerInterface>> MOTION_POWERS = List.of(
            SpeedPower::new,
            SoundPower::new,
            FlightPower::new
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // dark blue
    private static final DustParticleOptions BLUE_DEEP =
            new DustParticleOptions(new Vector3f(0.05f, 0.25f, 0.85f), 1.4f);
    // light blue
    private static final DustParticleOptions BLUE_LIGHT =
            new DustParticleOptions(new Vector3f(0.15f, 0.60f, 1.00f), 1.3f);
    // cyan
    private static final DustParticleOptions CYAN_BRIGHT =
            new DustParticleOptions(new Vector3f(0.20f, 0.90f, 1.00f), 1.1f);
    // bluey white
    private static final DustParticleOptions BLUE_PALE =
            new DustParticleOptions(new Vector3f(0.70f, 0.85f, 1.00f), 1.0f);
    // navy
    private static final DustParticleOptions NAVY =
            new DustParticleOptions(new Vector3f(0.00f, 0.08f, 0.35f), 1.5f);

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public MotionRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    public boolean tick(ServerLevel world) {
        // if they die or disconnect mid-ritual, clean up and cancel it
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayer sp) {
                cancelRitual(sp);
            }
            return true;
        }

        if (!(player instanceof ServerPlayer sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        // ambient loop plays evenly throughout the entire ritual
        if (ticks == 1 || ticks % 28 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALLOOP.get(), SoundSource.PLAYERS, 0.5f, 0.7f);
        }

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        if (stage < 4) lockPosition(sp);

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

        if (ticks >= TOTAL_TICKS) {
            onComplete(sp, world);
            return true;
        }

        return false;
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private int currentStage() {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) return 3;
        return 4;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    private void lockPosition(ServerPlayer sp) {
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;
        sp.fallDistance = 0;
        sp.setOnGround(true);
        sp.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 5, 10, true, false, false));
    }

    private void cancelRitual(ServerPlayer sp) {
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

            world.playSound(null, sp.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.6f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();

        // rings at feet that expand
        int    pulseTimer  = t % 12;
        double shockRadius = pulseTimer * 0.35;    // 0 to 4 blocks over 12 ticks
        if (shockRadius > 0.3 && t % 2 == 0) {
            int shockPoints = 14;
            for (int i = 0; i < shockPoints; i++) {
                double angle = Math.PI * 2.0 * i / shockPoints;
                DustParticleOptions col = (pulseTimer < 6) ? BLUE_LIGHT : BLUE_DEEP;
                world.sendParticles(col,
                        pos.x + Math.cos(angle) * shockRadius,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * shockRadius,
                        1, 0.04, 0.02, 0.04, 0.003);
            }
        }

        // rays from feet
        if (t % 5 == 0) {
            int    rays = 8;
            double maxR = 1.5 + progress * 1.5;
            for (int r = 0; r < rays; r++) {
                double angle = r * Math.PI * 2.0 / rays;
                for (double d = 0.4; d <= maxR; d += 0.5) {
                    world.sendParticles(BLUE_DEEP,
                            pos.x + Math.cos(angle) * d,
                            pos.y + 0.1,
                            pos.z + Math.sin(angle) * d,
                            1, 0.01, 0.02, 0.01, 0.015);
                }
            }
        }

        // trail
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double h = world.random.nextDouble() * (1.5 + progress * 1.5);
                world.sendParticles(BLUE_LIGHT,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                        pos.y + h,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                        1, 0.02, 0.04, 0.02, 0.010);
            }
        }

        // this looked better in my head
        // rarer sweep attacks
        if (t % 3 == 0) {
            world.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + 0.5 + world.random.nextDouble(),
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0, 0, 0, 1);
        }

        if (t % 20 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.ELYTRA_FLYING,
                    SoundSource.PLAYERS, 0.3f, 0.5f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.7f, 0.5f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // Four orbital rings orbiting at different radii, heights, and speeds.
        // using the orbit angle as input — this tilts the ring out of the horizontal
        // plane and makes it appear as a 3D angled orbit rather than a flat disc.
        // Columns: { radius, heightOffset, angularSpeedPerTick, yWobbleAmplitude, wobbleFreqMultiplier }
        double[][] rings = {
                { 2.5, 0.4,  0.08, 0.00, 0 },   // straight outer ring
                { 1.8, 1.0, -0.12, 0.55, 1 },   // tilted mid ring
                { 2.1, 0.7,  0.06, 0.00, 0 },   // flat medium ring
                { 1.3, 1.5, -0.10, 0.70, 2 },   // steeply tilted head ring
        };
        DustParticleOptions[] palette = { BLUE_DEEP, BLUE_LIGHT, CYAN_BRIGHT, BLUE_PALE };

        // Arc trail - spawn only a swept arc (not complete circle)
        int arcPoints = (int)(8 + progress * 10);

        for (int ri = 0; ri < rings.length; ri++) {
            double[] ring     = rings[ri];
            double   r        = ring[0];
            double   baseY    = pos.y + ring[1];
            double   speed    = ring[2];
            double   wobble   = ring[3];
            double   wobFreq  = ring[4];
            DustParticleOptions col = palette[ri];

            for (int i = 0; i < arcPoints; i++) {
                // Head of the arc at i=0, tail at i=arcPoints-1.
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double y     = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);

                if (t % 2 == 0 || i < 4) {
                    world.sendParticles(col,
                            pos.x + Math.cos(theta) * r,
                            y,
                            pos.z + Math.sin(theta) * r,
                            1, 0.03, 0.02, 0.03, 0.005);
                }

                // put a darker shadow behind the arc
                if (i == arcPoints - 1 && t % 3 == 0) {
                    world.sendParticles(NAVY,
                            pos.x + Math.cos(theta - 0.15) * r,
                            y,
                            pos.z + Math.sin(theta - 0.15) * r,
                            1, 0.04, 0.02, 0.04, 0.003);
                }
            }
        }

        // little burst between passes - not really noticable
        if (t % 8 == 0) {
            int    rays = 6;
            double gyroTime = time * 0.03;
            for (int r = 0; r < rays; r++) {
                double angle = r * Math.PI * 2.0 / rays + gyroTime;
                world.sendParticles(BLUE_LIGHT,
                        pos.x + Math.cos(angle) * 1.2,
                        pos.y + 0.5,
                        pos.z + Math.sin(angle) * 1.2,
                        1, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05, 0.02);
            }
        }

        if (t == 20) world.playSound(null, sp.blockPosition(),
                ModSounds.GUST.get(),  SoundSource.PLAYERS, 0.5f, 1.4f);
        if (t == 40) world.playSound(null, sp.blockPosition(),
                SoundEvents.ELYTRA_FLYING,  SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.5f);
        }

        double progress  = (double) t / STAGE_3_TICKS;
        Vec3 pos       = sp.position();
        long   time      = world.getGameTime();
        double shrink    = 1.0 - progress * 0.65;
        double speedMult = 1.0 + progress * 1.5;

        double[][] rings = {
                { 2.5 * shrink, 0.4,  0.08 * speedMult, 0.00, 0 },
                { 1.8 * shrink, 1.0, -0.12 * speedMult, 0.55, 1 },
                { 2.1 * shrink, 0.7,  0.06 * speedMult, 0.00, 0 },
                { 1.3 * shrink, 1.5, -0.10 * speedMult, 0.70, 2 },
        };
        DustParticleOptions[] palette = { BLUE_DEEP, BLUE_LIGHT, CYAN_BRIGHT, BLUE_PALE };

        int arcPoints = (int)(12 + progress * 8);

        for (int ri = 0; ri < rings.length; ri++) {
            double[] ring   = rings[ri];
            double   r      = ring[0];
            double   baseY  = pos.y + ring[1];
            double   speed  = ring[2];
            double   wobble = ring[3];
            double   wobFreq = ring[4];
            DustParticleOptions col = palette[ri];

            for (int i = 0; i < arcPoints; i++) {
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double y     = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);
                if (t % 2 == 0) {
                    world.sendParticles(col,
                            pos.x + Math.cos(theta) * r,
                            y,
                            pos.z + Math.sin(theta) * r,
                            1, 0.04, 0.03, 0.04, 0.008);
                }
            }
        }

        // tighten it to shrink towards player
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle  = world.random.nextDouble() * Math.PI * 2;
                double srcR   = 3.5 + world.random.nextDouble() * 1.5;
                double srcY   = pos.y + world.random.nextDouble() * 2.0;
                Vec3 from   = new Vec3(
                        pos.x + Math.cos(angle) * srcR, srcY, pos.z + Math.sin(angle) * srcR);
                Vec3 toward  = pos.add(0, 1, 0).subtract(from)
                        .normalize().scale(0.10 + progress * 0.06);
                DustParticleOptions col = world.random.nextBoolean() ? BLUE_LIGHT : CYAN_BRIGHT;
                world.sendParticles(col,
                        from.x, from.y, from.z,
                        1, toward.x, toward.y, toward.z, 0.014);
            }
        }

        // Horizontal shockwave rings at waist and knee
        if (t % 10 == 0) {
            for (double shockY : new double[]{ 0.5, 1.3 }) {
                int shockPoints = 16;
                for (int i = 0; i < shockPoints; i++) {
                    double angle = Math.PI * 2.0 * i / shockPoints;
                    double vel   = 0.06 + progress * 0.04;
                    world.sendParticles(CYAN_BRIGHT,
                            pos.x + Math.cos(angle) * 0.3,
                            pos.y + shockY,
                            pos.z + Math.sin(angle) * 0.3,
                            1, Math.cos(angle) * vel, 0.005, Math.sin(angle) * vel, 0.0);
                }
            }
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                    (float)(0.4 + progress * 0.3), 1.3f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,      SoundSource.PLAYERS, 0.8f, 1.6f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.3f);

            // particles fired in rays from the player
            Vec3 pos = sp.position();
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI * 2.0 / 8;
                for (double r = 0.5; r <= 3.0; r += 0.5) {
                    world.sendParticles(BLUE_LIGHT,
                            pos.x + Math.cos(angle) * r, pos.y + 0.5, pos.z + Math.sin(angle) * r,
                            1, Math.cos(angle) * 0.08, 0.02, Math.sin(angle) * 0.08, 0.0);
                }
            }
            world.sendParticles(CYAN_BRIGHT, pos.x, pos.y + 1.0, pos.z,
                    16, 1.5, 1.2, 1.5, 0.12);
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z,
                    4, 0.8, 0.5, 0.8, 0);
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;

        // effects
        if (progress > 0.15f && progress < 0.82f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 25, 0, true, false, false));
        }
        // damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // rotate rays around the player
        int rayCount = (int)(12 + (1.0 - progress) * 12);
        if (t % 2 == 0) {
            for (int r = 0; r < rayCount; r++) {
                double angle = r * Math.PI * 2.0 / rayCount + (ticks * 0.07);
                double vel   = 0.08 + (1.0 - progress) * 0.05;
                double h     = 0.3 + world.random.nextDouble() * 2.0;
                DustParticleOptions col = switch (r % 3) {
                    case 0  -> BLUE_DEEP;
                    case 1  -> BLUE_LIGHT;
                    default -> CYAN_BRIGHT;
                };
                world.sendParticles(col,
                        pos.x + Math.cos(angle) * 0.5,
                        pos.y + h,
                        pos.z + Math.sin(angle) * 0.5,
                        1, Math.cos(angle) * vel, 0.01, Math.sin(angle) * vel, 0.0);
            }
        }

        // more sweeps
        if (t % 6 == 0) {
            world.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + 0.5 + world.random.nextDouble() * 1.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0, 0, 0, 1);
        }

        // calm it
        if (progress > 0.80f) {
            for (int i = 0; i < 3; i++) {
                world.sendParticles(BLUE_PALE,
                        pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.004);
                world.sendParticles(NAVY,
                        pos.x, pos.y + 0.5, pos.z, 1, 0.4, 0.2, 0.4, 0.003);
            }
        }

        if (t % 5 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.ELYTRA_FLYING,
                    SoundSource.PLAYERS,
                    0.5f + (float) progress * 0.3f, 1.5f + (float) progress * 0.5f);
        }
        if (t % 8 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSLOOP.get(),
                    SoundSource.PLAYERS,  0.6f, 1.6f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        Supplier<PowerInterface> factory =
                MOTION_POWERS.get(sp.getRandom().nextInt(MOTION_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;
        sp.clearFire();
        sp.heal(3.0f);

        Vec3 pos = sp.position();

        // final fx
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI * 2.0 / 8;
            world.sendParticles(BLUE_LIGHT,
                    pos.x + Math.cos(angle) * 0.5, pos.y + 1.0, pos.z + Math.sin(angle) * 0.5,
                    4, Math.cos(angle) * 0.10, 0.05, Math.sin(angle) * 0.10, 0.0);
        }
        world.sendParticles(CYAN_BRIGHT, pos.x, pos.y + 1.0, pos.z, 12, 1.2, 1.0, 1.2, 0.09);
        world.sendParticles(BLUE_PALE,   pos.x, pos.y + 1.0, pos.z,  8, 0.8, 0.7, 0.8, 0.05);
        world.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z,
                3, 0.5, 0.3, 0.5, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 0.9f);
    }
}