package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import net.minecraft.ChatFormatting;
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
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

public class PerfectedUpgradeRitual implements RitualInterface {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float DAMAGE_PER_TICK = 0.35f;
    private static final int   DAMAGE_INTERVAL = 10;
    private static final float MIN_HEALTH      = 0.5f;

    /* ============================================================
       CAMERA SHAKE
       ============================================================ */

    private static final int   SHAKE_RADIUS            = 8;   // wider than upgrade ritual
    private static final int   SHAKE_STAGE_1_INTERVAL  = 18;
    private static final int   SHAKE_STAGE_2_INTERVAL  = 10;
    private static final int   SHAKE_STAGE_3_INTERVAL  = 6;
    private static final int   SHAKE_STAGE_4_INTERVAL  = 4;   // very frequent at peak
    private static final float SHAKE_STAGE_1_INTENSITY = 0.10f;
    private static final float SHAKE_STAGE_2_INTENSITY = 0.20f;
    private static final float SHAKE_STAGE_3_INTENSITY = 0.28f;
    private static final float SHAKE_STAGE_4_BURST     = 0.50f;  //
    private static final float SHAKE_STAGE_4_BASE      = 0.30f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // magenta
    private static final DustParticleOptions MAGENTA =
            new DustParticleOptions(new Vector3f(0.95f, 0.15f, 0.70f), 1.5f);
    // white pink
    private static final DustParticleOptions PINK_WHITE =
            new DustParticleOptions(new Vector3f(1.00f, 0.62f, 0.88f), 1.2f);
    // purple
    private static final DustParticleOptions PURPLE =
            new DustParticleOptions(new Vector3f(0.52f, 0.08f, 0.80f), 1.5f);
    // violet
    private static final DustParticleOptions VIOLET =
            new DustParticleOptions(new Vector3f(0.75f, 0.35f, 0.95f), 1.3f);
    // yellow white
    private static final DustParticleOptions WHITE_YELLOW =
            new DustParticleOptions(new Vector3f(0.96f, 0.94f, 1.00f), 1.3f);
    // white
    private static final DustParticleOptions WHITE =
            new DustParticleOptions(new Vector3f(1.00f, 0.98f, 1.00f), 1.8f);

    // SIGIL GEOMETRY

    private static final double[] SIGIL_ANGLES = {
            0.0,
            Math.PI * 0.28,
            Math.PI * 0.55,
            Math.PI * 0.82,
            Math.PI,
            Math.PI * 1.25,
            Math.PI * 1.55,
            Math.PI * 1.80
    };

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PerfectedUpgradeRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    @Override
    public boolean tick(ServerLevel world) {
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayer sp) {
                cancelRitual(sp);
            }
            return true;
        }

        if (!(player instanceof ServerPlayer sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

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
       STAGE HELPERS
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
        sp.removeEffect(MobEffects.CONFUSION); // NAUSEA -> CONFUSION in Mojmap
    }

    /* ============================================================
       SHARED HELPERS
       ============================================================ */

    private void spawnFallingColumn(ServerLevel world, Vec3 pos,
                                    int count, double radius, double minH, double maxH) {
        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * radius;
            DustParticleOptions col = world.random.nextBoolean() ? MAGENTA : WHITE_YELLOW;
            world.sendParticles(col,
                    pos.x + Math.cos(angle) * r,
                    pos.y + minH + world.random.nextDouble() * (maxH - minH),
                    pos.z + Math.sin(angle) * r,
                    1, 0.02, -0.10, 0.02, 0.004);
        }
    }

    private void spawnHalo(ServerLevel world, Vec3 pos, double radius, double height,
                           double speed, int points,
                           DustParticleOptions primary, DustParticleOptions secondary) {
        for (int i = 0; i < points; i++) {
            double angle = speed + i * Math.PI * 2.0 / points;
            DustParticleOptions col = (i % 2 == 0) ? primary : secondary;
            world.sendParticles(col,
                    pos.x + Math.cos(angle) * radius,
                    pos.y + height,
                    pos.z + Math.sin(angle) * radius,
                    1, 0.01, 0.015, 0.01, 0.005);
        }
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_AMBIENT,     SoundSource.PLAYERS, 1.0f, 0.45f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 0.25f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        if (t % SHAKE_STAGE_1_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_1_INTENSITY + (float) progress * 0.05f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // sigil on ground
        double sigRotation = time * 0.020;
        double spokeReach  = 1.0 + progress * 4.0;

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + (double) s / steps * spokeReach;
                    // Inner spoke: deep purple; outer: violet then blush at the tip
                    DustParticleOptions col = (s < 4) ? PURPLE
                            : (s < 7) ? VIOLET
                            : PINK_WHITE;
                    double jitter = (world.random.nextDouble() - 0.5) * 0.14;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.01, 0.01, 0.01, 0.003);
                }

                // tip
                if (t % 5 == 0 && spokeReach > 1.0) {
                    world.sendParticles(MAGENTA,
                            pos.x + Math.cos(angle) * spokeReach,
                            pos.y + 0.10,
                            pos.z + Math.sin(angle) * spokeReach,
                            1, 0.04, 0.06, 0.04, 0.014);
                }
            }

            // ring joining
            if (spokeReach > 1.5) {
                int ringPoints = 32;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                    DustParticleOptions col = (i % 3 == 0) ? MAGENTA : PURPLE;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * spokeReach,
                            pos.y + 0.06,
                            pos.z + Math.sin(angle) * spokeReach,
                            1, 0.01, 0.01, 0.01, 0.003);
                }
            }
        }

        // column
        if (t % 2 == 0) {
            spawnFallingColumn(world, pos, (int)(3 + progress * 7), 1.4, 7.0, 13.0);
        }

        // fx rising
        if (t % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + world.random.nextDouble() * spokeReach * 0.7;
                world.sendParticles(VIOLET,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1 + world.random.nextDouble() * 2.5 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.04, 0.01, 0.008);
            }
        }

        // extra fx
        if (t % 14 == 0) {
            world.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z,
                    12, 0.8, 0.8, 0.8, 1.6);
        }

        // glow at tips
        if (t % 7 == 0 && progress > 0.25f) {
            double spokeAngle = SIGIL_ANGLES[world.random.nextInt(SIGIL_ANGLES.length)] + sigRotation;
            world.sendParticles(ParticleTypes.GLOW,
                    pos.x + Math.cos(spokeAngle) * spokeReach,
                    pos.y + 0.1,
                    pos.z + Math.sin(spokeAngle) * spokeReach,
                    2, 0.05, 0.05, 0.05, 0);
        }

        if (t % 18 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.6f, 0.35f + (float) progress * 0.45f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.9f, 0.65f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE,       SoundSource.PLAYERS, 0.8f, 0.60f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // shake
        if (t % SHAKE_STAGE_2_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_2_INTENSITY + (float) progress * (SHAKE_STAGE_3_INTENSITY - SHAKE_STAGE_2_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // rise and rotate sigil
        double sigRotation = time * 0.035;   // faster
        double sigilY      = pos.y + progress * 1.2;   // rises to chest

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 9;
                for (int s = 0; s < steps; s++) {
                    double d   = 0.3 + (double) s / steps * 5.0;
                    DustParticleOptions col = (s < 3) ? PURPLE
                            : (s < 6) ? VIOLET
                            : MAGENTA;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * d,
                            sigilY,
                            pos.z + Math.sin(angle) * d,
                            1, 0.02, 0.015, 0.02, 0.003);
                }
            }

            // Outer ring of the floating sigil
            int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                DustParticleOptions col = (i % 2 == 0) ? PURPLE : PINK_WHITE;
                world.sendParticles(col,
                        pos.x + Math.cos(angle) * 5.0,
                        sigilY,
                        pos.z + Math.sin(angle) * 5.0,
                        1, 0.01, 0.01, 0.01, 0.003);
            }
        }

        // three halos at varied sizes
        if (t % 2 == 0) {
            // Outer — pink, slow clockwise
            spawnHalo(world, pos, 2.4, 1.6, time * 0.07, 24, MAGENTA, VIOLET);
        }
        if (t % 2 == 0) {
            // Mid — purple, counter-clockwise
            spawnHalo(world, pos, 1.7, 1.9, -time * 0.10, 18, PURPLE, WHITE_YELLOW);
        }
        if (t % 3 == 0) {
            // Inner — white, fast clockwise
            spawnHalo(world, pos, 1.0, 2.2, time * 0.14, 12, WHITE_YELLOW, VIOLET);
        }

        // beams from each sigil spoke
        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                double cx    = pos.x + Math.cos(angle) * 0.7;
                double cz    = pos.z + Math.sin(angle) * 0.7;
                int    steps = (int)(5 + progress * 7);
                for (int s = 0; s < steps; s++) {
                    double h   = pos.y + 0.3 + s * (0.6 + progress * 0.4);
                    DustParticleOptions col = switch (s % 3) {
                        case 0  -> MAGENTA;
                        case 1  -> PURPLE;
                        default -> WHITE_YELLOW;
                    };
                    world.sendParticles(col,
                            cx + (world.random.nextDouble() - 0.5) * 0.20,
                            h,
                            cz + (world.random.nextDouble() - 0.5) * 0.20,
                            1, 0.02, 0.06, 0.02, 0.006);
                }
            }
        }

        // column
        if (t % 2 == 0) {
            spawnFallingColumn(world, pos, (int)(6 + progress * 10), 1.0, 7.0, 14.0);
        }

        // blindness
        if (progress > 0.35f && progress < 0.92f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // damage
        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), DAMAGE_PER_TICK);
            }
        }

        if (t == 22) world.playSound(null, sp.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_PLACE,  SoundSource.PLAYERS, 0.7f, 0.45f);
        if (t == 46) world.playSound(null, sp.blockPosition(),
                SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.75f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 0.8f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT,  SoundSource.PLAYERS, 0.7f, 1.1f);
            // Shake spike as stage 3 opens
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_3_INTENSITY + 0.05f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // shake
        if (t % SHAKE_STAGE_3_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_3_INTENSITY
                    + (float) progress * (SHAKE_STAGE_4_BASE - SHAKE_STAGE_3_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // compress sigil
        double sigRotation = time * 0.055;   // fastest the sigil spins
        double shrink      = 1.0 - progress * 0.84;
        double sigR        = 5.0 * shrink + 0.8;
        double sigY        = pos.y + 1.2 - progress * 0.6;   // descends back toward feet

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 8;
                for (int s = 0; s < steps; s++) {
                    double d   = 0.2 + (double) s / steps * sigR;
                    DustParticleOptions col = (s % 2 == 0) ? PURPLE : MAGENTA;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * d,
                            sigY,
                            pos.z + Math.sin(angle) * d,
                            1, 0.03, 0.02, 0.03, 0.005);
                }
            }
        }

        // halos shrinking
        double haloShrink = 1.0 - progress * 0.55;
        if (t % 2 == 0) {
            spawnHalo(world, pos, 2.4 * haloShrink, 1.6,  time * (0.07 + progress * 0.08), 24,
                    MAGENTA, VIOLET);
            spawnHalo(world, pos, 1.7 * haloShrink, 1.9, -time * (0.10 + progress * 0.08), 18,
                    PURPLE, WHITE_YELLOW);
        }
        if (t % 2 == 0) {
            spawnHalo(world, pos, 1.0 * haloShrink, 2.2,  time * (0.14 + progress * 0.12), 12,
                    WHITE_YELLOW, VIOLET);
        }

        // particle pull
        if (t % 2 == 0) {
            int pullCount = (int)(10 + progress * 18);
            for (int i = 0; i < pullCount; i++) {
                double theta  = world.random.nextDouble() * Math.PI * 2;
                double phi    = world.random.nextDouble() * Math.PI;
                double srcR   = 3.5 + world.random.nextDouble() * 5.0;
                double fromX  = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = pos.y + 1.0 + Math.cos(phi) * srcR * 0.45;
                double fromZ  = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3 toward = pos.add(0, 1, 0)
                        .subtract(fromX, fromY, fromZ)
                        .normalize()
                        .scale(0.10 + progress * 0.07);

                DustParticleOptions col = switch (i % 4) {
                    case 0  -> MAGENTA;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    default -> WHITE_YELLOW;
                };
                world.sendParticles(col, fromX, fromY, fromZ,
                        1, toward.x, toward.y, toward.z, 0.010);
            }
        }

        // outward eruptions
        if (t % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI * 0.5;
                double speed = 0.10 + progress * 0.05;
                DustParticleOptions col = (i % 2 == 0) ? MAGENTA : WHITE_YELLOW;
                world.sendParticles(col,
                        pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.5 + 0.04,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // column
        if (t % 2 == 0) {
            spawnFallingColumn(world, pos, (int)(10 + progress * 10), 0.7, 8.0, 15.0);
        }

        // effects
        sp.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, 15, 0, true, false, false));
        if (progress > 0.40f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION, 30, 0, true, false, false)); // NAUSEA -> CONFUSION
        }

        // Damage continues
        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), DAMAGE_PER_TICK);
            }
        }

        if (t % 8 == 0) {
            world.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z, 10, 0.6, 0.7, 0.6, 1.8);
        }

        if (t % 10 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS,
                    (float)(0.6 + progress * 0.4), 0.9f + (float) progress * 0.5f);
        }
        if (t == 30) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,      SoundSource.PLAYERS, 1.3f, 0.65f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9f, 0.75f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT,     SoundSource.PLAYERS, 1.0f, 0.80f);

            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_4_BURST);

            // shoot particles out
            Vec3 pos = sp.position();
            for (int d = 0; d < 48; d++) {
                double theta = d * Math.PI * 2.0 / 48;
                double phi   = world.random.nextDouble() * Math.PI;
                double speed = 0.22 + world.random.nextDouble() * 0.16;
                DustParticleOptions col = switch (d % 5) {
                    case 0  -> MAGENTA;
                    case 1  -> WHITE_YELLOW;
                    case 2  -> PURPLE;
                    case 3  -> VIOLET;
                    default -> WHITE;
                };
                world.sendParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }

            // shockwave rings
            for (double ringScale : new double[]{ 0.5, 1.0, 1.6 }) {
                int ringPoints = 20;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = i * Math.PI * 2.0 / ringPoints;
                    world.sendParticles(
                            ringScale < 1.0 ? WHITE : PURPLE,
                            pos.x + Math.cos(angle) * ringScale * 0.4,
                            pos.y + 0.15,
                            pos.z + Math.sin(angle) * ringScale * 0.4,
                            1,
                            Math.cos(angle) * 0.32 * ringScale,
                            0.01,
                            Math.sin(angle) * 0.32 * ringScale,
                            0.0);
                }
            }

            world.sendParticles(ParticleTypes.FLASH,  pos.x, pos.y + 1.2, pos.z, 5, 0.4, 0.4, 0.4, 0);
            world.sendParticles(ParticleTypes.GLOW,   pos.x, pos.y + 1.0, pos.z, 20, 0.8, 0.7, 0.8, 0);
            world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                    20, 1.0, 1.2, 1.0, 0.20);
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;
        long   time     = world.getGameTime();

        // decay shake
        int shakeInterval = (progress < 0.60) ? SHAKE_STAGE_4_INTERVAL : SHAKE_STAGE_3_INTERVAL;
        if (t % shakeInterval == 0) {
            float intensity = SHAKE_STAGE_4_BASE * (float)(1.0 - progress * 0.70);
            intensity = Math.max(intensity, SHAKE_STAGE_1_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }


        // blindness
        if (progress < 0.72f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // dense column
        if (t % 2 == 0 && progress < 0.70f) {
            spawnFallingColumn(world, pos,
                    (int)(14 + (1.0 - progress) * 10), 0.6, 8.0, 16.0);
        }

        // fast halos
        double finalShrink = 0.7 - progress * 0.25;
        if (t % 2 == 0 && progress < 0.80f) {
            spawnHalo(world, pos, Math.max(0.3, 2.4 * finalShrink), 1.6,
                    time * 0.18, 24, MAGENTA, WHITE);
            spawnHalo(world, pos, Math.max(0.3, 1.7 * finalShrink), 1.9,
                    -time * 0.22, 18, PURPLE, WHITE_YELLOW);
            spawnHalo(world, pos, Math.max(0.2, 1.0 * finalShrink), 2.2,
                    time * 0.28, 12, WHITE, VIOLET);
        }

        // more particles bursting out
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI * 0.7;
                double speed = 0.08 + (1.0 - progress) * 0.06;
                world.sendParticles(WHITE,
                        pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.6 + 0.03,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // totem stuff
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.7,
                        pos.y + 0.3 + world.random.nextDouble() * 1.8,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.7,
                        1,
                        (world.random.nextDouble() - 0.5) * 0.04,
                        0.10 + world.random.nextDouble() * 0.12,
                        (world.random.nextDouble() - 0.5) * 0.04,
                        0.0);
            }
        }

        // calming
        if (progress > 0.75f) {
            world.sendParticles(WHITE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.004);
            world.sendParticles(PINK_WHITE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.003);
        }

        // Enchant throughout stage
        if (t % 8 == 0) {
            world.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z, 10, 0.6, 0.8, 0.6, 2.0);
        }

        if (t % 7 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS,
                    0.6f + (float) progress * 0.4f, 0.65f + (float) progress * 1.0f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        PowerManager.setLevel(sp, 3);

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.CONFUSION);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;

        // Heal
        sp.heal(6.0f);

        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.28f);

        Vec3 pos = sp.position();

        for (int d = 0; d < 40; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi   = world.random.nextDouble() * Math.PI;
            double speed = 0.18;
            DustParticleOptions col = switch (d % 4) {
                case 0  -> WHITE;
                case 1  -> MAGENTA;
                case 2  -> PURPLE;
                default -> WHITE_YELLOW;
            };
            world.sendParticles(col, pos.x, pos.y + 1.0, pos.z,
                    1,
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(phi) * Math.sin(theta) * speed,
                    0.0);
        }

        // Eight-point sigil star fired
        for (double angle : SIGIL_ANGLES) {
            for (double d = 0.5; d <= 4.0; d += 0.6) {
                world.sendParticles(d < 2.0 ? WHITE : MAGENTA,
                        pos.x + Math.cos(angle) * d,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * d,
                        1,
                        Math.cos(angle) * 0.12, 0.01, Math.sin(angle) * 0.12,
                        0.0);
            }
        }

        world.sendParticles(WHITE, pos.x, pos.y + 1.0, pos.z, 22, 1.5, 1.2, 1.5, 0.10);
        world.sendParticles(MAGENTA,     pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        world.sendParticles(PURPLE,     pos.x, pos.y + 1.0, pos.z, 16, 1.1, 1.0, 1.1, 0.08);
        world.sendParticles(WHITE_YELLOW,      pos.x, pos.y + 1.0, pos.z, 14, 1.0, 0.9, 1.0, 0.07);
        world.sendParticles(VIOLET,     pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                24, 1.2, 1.5, 1.2, 0.22);
        world.sendParticles(ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z,
                16, 1.0, 0.8, 1.0, 0);
        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.4, pos.z,
                5, 0.3, 0.3, 0.3, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.5f);
        world.playSound(null, sp.blockPosition(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 1.0f, 1.4f);
        world.playSound(null, sp.blockPosition(),
                SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0f, 1.6f);
        world.playSound(null, sp.blockPosition(),
                SoundEvents.ELDER_GUARDIAN_CURSE,
                SoundSource.PLAYERS, 0.6f, 2.0f);

        sp.sendSystemMessage(
                Component.translatable("ritual.loopypowers.upgrade.level_3")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
        );
    }
}