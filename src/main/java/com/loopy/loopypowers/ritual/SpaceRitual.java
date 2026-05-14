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

public class SpaceRitual implements RitualInterface {

    /* ============================================================
       POWERS
       ============================================================ */

    private static final List<Supplier<PowerInterface>> SPACE_POWERS = List.of(
            CosmicPower::new,
            TeleportPower::new,
            DimensionalPower::new
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

    // Height above the player's feet where the stage-3 singularity forms
    private static final double SINGULARITY_HEIGHT = 3.8;

    // arm geometry
    private static final double ARM_A     = 0.35;
    private static final double ARM_B     = 0.50;
    private static final double ARM_T_MAX = 2.7 * Math.PI;
    private static final int    ARM_STEPS = 30;

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleOptions PURPLE =
            new DustParticleOptions(new Vector3f(0.52f, 0.08f, 0.80f), 1.4f);
    private static final DustParticleOptions PURPLE_DARK =
            new DustParticleOptions(new Vector3f(0.20f, 0.02f, 0.38f), 1.6f);
    private static final DustParticleOptions PINK =
            new DustParticleOptions(new Vector3f(0.88f, 0.40f, 0.94f), 1.2f);
    private static final DustParticleOptions GOLD =
            new DustParticleOptions(new Vector3f(1.00f, 0.92f, 0.58f), 0.9f);
    private static final DustParticleOptions WHITE =
            new DustParticleOptions(new Vector3f(0.92f, 0.96f, 1.00f), 0.7f);
    private static final DustParticleOptions BLUE =
            new DustParticleOptions(new Vector3f(0.22f, 0.82f, 0.90f), 1.1f);
    private static final DustParticleOptions BLUE_lIGHT =
            new DustParticleOptions(new Vector3f(0.78f, 0.92f, 1.00f), 1.3f);

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public SpaceRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

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
        sp.removeEffect(MobEffects.LEVITATION);
    }

    /* ============================================================
       START FIELD (used by many stages)
       ============================================================ */

    private void spawnStarField(ServerPlayer sp, ServerLevel world, int starsPerTick) {
        Vec3 pos = sp.position();
        for (int i = 0; i < starsPerTick; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = 5.5 + world.random.nextDouble() * 6.5;
            double h     = -0.5 + world.random.nextDouble() * 13.0;  // from feet to 12.5

            double sx = pos.x + Math.cos(angle) * r;
            double sy = pos.y + h;
            double sz = pos.z + Math.sin(angle) * r;

            // smaller chance of end rod instead of dust
            if (world.random.nextInt(5) == 0) {
                world.sendParticles(ParticleTypes.END_ROD, sx, sy, sz,
                        1, 0, 0, 0, 0);
            } else {
                DustParticleOptions star = (world.random.nextInt(3) == 0) ? GOLD : WHITE;
                world.sendParticles(star, sx, sy, sz,
                        1, 0.005, 0.005, 0.005, 0.001);
            }
        }
    }

    /* ============================================================
       ARMS (also used by multiple stages)
       ============================================================ */

    private void spawnArmsFlat(ServerLevel world, Vec3 pos, double tMax,
                               double armY, double cloudWidth,
                               double rotation, boolean dim) {
        int steps = Math.max(3, (int)(ARM_STEPS * (tMax / ARM_T_MAX)));

        for (int arm = 0; arm < 2; arm++) {
            double armOffset = arm * Math.PI;

            for (int s = 0; s < steps; s++) {
                double theta     = (double) s / steps * tMax;
                double r         = ARM_A + ARM_B * theta;
                double angle     = theta + armOffset + rotation;
                // Cloud width scales with radius
                double cloud     = cloudWidth * (0.25 + (r / 4.6) * 0.75);

                double px = pos.x + Math.cos(angle) * r
                        + (world.random.nextDouble() - 0.5) * cloud;
                double pz = pos.z + Math.sin(angle) * r
                        + (world.random.nextDouble() - 0.5) * cloud;

                DustParticleOptions col;
                if (dim) {
                    col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                } else {
                    double frac = (double) s / steps;
                    col = (frac < 0.35) ? PURPLE_DARK
                            : (frac < 0.70) ? PURPLE
                            : (s % 2 == 0)  ? PINK
                            : BLUE;
                }

                world.sendParticles(col, px, armY, pz,
                        1, 0.01, 0.01, 0.01, 0.002);
            }
        }
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.7f, 0.35f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();
        double rotation = time * 0.018;  // rotation speed

        // stars nearby, ramps up
        spawnStarField(sp, world, (int)(4 + progress * 8));

        // arms from players feet
        double armTMax = ARM_T_MAX * progress;
        if (armTMax > 0.4 && t % 2 == 0) {
            spawnArmsFlat(world, pos, armTMax, pos.y + 0.08, 0.35, rotation, false);
        }

        // wisps around
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 1.5;
                world.sendParticles(PURPLE_DARK,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1 + world.random.nextDouble() * 2.5 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.03, 0.01, 0.006);
            }
        }

        // enchant particles
        if (t % 14 == 0) {
            world.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z,
                    6, 0.5, 0.5, 0.5, 1.2);
        }

        if (t % 24 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.4f, 0.28f + (float) progress * 0.35f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.7f, 0.45f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();
        double rotation = time * 0.018;

        // make stars more dense
        spawnStarField(sp, world, 12);

        // spiral is bigger with new particles
        if (t % 2 == 0) {
            for (int arm = 0; arm < 2; arm++) {
                double armOffset = arm * Math.PI;

                for (int s = 0; s < ARM_STEPS; s++) {
                    double theta  = (double) s / ARM_STEPS * ARM_T_MAX;
                    double r      = ARM_A + ARM_B * theta;
                    double angle  = theta + armOffset + rotation;
                    // y rises with theta, amplified by progress so the arms are flat
                    // at stage start and fully elevated by stage end
                    double armY   = pos.y + 0.1 + theta * (0.48 * progress);
                    double cloudW = 0.25 + (r / 4.6) * 0.85;

                    double px = pos.x + Math.cos(angle) * r
                            + (world.random.nextDouble() - 0.5) * cloudW;
                    double py = armY + (world.random.nextDouble() - 0.5) * cloudW * 0.45;
                    double pz = pos.z + Math.sin(angle) * r
                            + (world.random.nextDouble() - 0.5) * cloudW;

                    double frac = (double) s / ARM_STEPS;
                    DustParticleOptions col;
                    if (frac < 0.30) {
                        col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                    } else if (frac < 0.65) {
                        col = (s % 2 == 0) ? PURPLE : PINK;
                    } else {
                        col = (s % 2 == 0) ? PINK : BLUE;
                    }
                    world.sendParticles(col, px, py, pz,
                            1, 0.01, 0.02, 0.01, 0.003);
                }
            }
        }

        // tilted orbital ring
        double orbitR     = 2.7;
        double orbitSpeed = time * 0.09;
        double orbitTilt  = 0.32;   // radians of tilt off horizontal
        int    orbitPts   = 18;
        if (t % 2 == 0) {
            for (int i = 0; i < orbitPts; i++) {
                double theta = orbitSpeed + i * Math.PI * 2.0 / orbitPts;
                double ox    = pos.x + Math.cos(theta) * orbitR;
                double oy    = pos.y + 1.6 + Math.sin(theta) * orbitR * Math.sin(orbitTilt);
                double oz    = pos.z + Math.sin(theta) * orbitR * Math.cos(orbitTilt);
                DustParticleOptions col = (i % 3 == 0) ? GOLD : BLUE_lIGHT;
                world.sendParticles(col, ox, oy, oz,
                        1, 0.01, 0.01, 0.01, 0.004);
            }
        }

        // purple fx around
        if (t % 6 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 1.0 + world.random.nextDouble() * 3.5;
                double h     = 0.4 + world.random.nextDouble() * (3.0 + progress * 2.0);
                world.sendParticles(ParticleTypes.DRAGON_BREATH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.01, 0.02, 0.005);
            }
        }

        if (t == 18) world.playSound(null, sp.blockPosition(),
                SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.5f, 0.65f);
        if (t == 42) world.playSound(null, sp.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.5f, 0.45f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.65f);
        }

        double progress  = (double) t / STAGE_3_TICKS;
        Vec3 pos       = sp.position();
        long   time      = world.getGameTime();
        double rotation  = time * 0.018;

        // coords of singularity
        double singX = pos.x;
        double singY = pos.y + SINGULARITY_HEIGHT;
        double singZ = pos.z;

        // very noticeable star field
        spawnStarField(sp, world, 16);

        // dim and shrink arms
        double armShrink = 1.0 - progress * 0.50;
        if (t % 3 == 0) {
            spawnArmsFlat(world, pos, ARM_T_MAX * armShrink,
                    pos.y + 0.08, 0.55, rotation, true);
        }

        // spinning disc around singularity point
        double discR     = 1.9 - progress * 0.5;
        double discSpeed = time * (0.14 + progress * 0.18);
        int    discPts   = 24;
        if (t % 2 == 0) {
            for (int i = 0; i < discPts; i++) {
                double theta = discSpeed + i * Math.PI * 2.0 / discPts;
                double dx    = pos.x + Math.cos(theta) * discR;
                double dz    = pos.z + Math.sin(theta) * discR;

                DustParticleOptions col = switch (i % 4) {
                    case 0  -> BLUE_lIGHT;
                    case 1  -> WHITE;
                    case 2  -> PURPLE;
                    default -> BLUE;
                };
                world.sendParticles(col, dx, singY - 0.1, dz,
                        1, 0.03, 0.02, 0.03, 0.006);

                // Inner halo slightly above the disc plane
                if (i % 3 == 0) {
                    world.sendParticles(BLUE_lIGHT,
                            pos.x + Math.cos(theta + 0.2) * (discR * 0.58),
                            singY + 0.12,
                            pos.z + Math.sin(theta + 0.2) * (discR * 0.58),
                            1, 0.02, 0.02, 0.02, 0.005);
                }
            }
        }

        // centre of singularity
        if (t % 2 == 0) {
            world.sendParticles(ParticleTypes.PORTAL,
                    singX, singY, singZ,
                    8, 0.28, 0.18, 0.28, 0.06);
        }

        // particles shot from singularity
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                world.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        singX + (world.random.nextDouble() - 0.5) * 0.2,
                        singY,
                        singZ + (world.random.nextDouble() - 0.5) * 0.2,
                        1, 0.05, 0.12, 0.05, 0.04);
            }
        }

        // pulls particles into singularity
        int pullCount = (int)(8 + progress * 16);
        if (t % 2 == 0) {
            for (int i = 0; i < pullCount; i++) {
                double theta  = world.random.nextDouble() * Math.PI * 2;
                double phi    = world.random.nextDouble() * Math.PI;
                double srcR   = 4.5 + world.random.nextDouble() * 5.5;
                double fromX  = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = pos.y + 1.0 + Math.cos(phi) * srcR * 0.5 + 2.0;
                double fromZ  = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3 toward = new Vec3(singX, singY, singZ)
                        .subtract(fromX, fromY, fromZ)
                        .normalize()
                        .scale(0.09 + progress * 0.07);

                DustParticleOptions col = switch (world.random.nextInt(4)) {
                    case 0  -> WHITE;
                    case 1  -> PURPLE;
                    case 2  -> BLUE_lIGHT;
                    default -> GOLD;
                };
                world.sendParticles(col, fromX, fromY, fromZ,
                        1, toward.x, toward.y, toward.z, 0.010);
            }
        }

        if (t % 12 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS,
                    (float)(0.5 + progress * 0.5), 0.65f + (float) progress * 0.45f);
        }
        if (t == 32) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.3f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WITHER_SPAWN,           SoundSource.PLAYERS, 0.9f, 0.55f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 0.65f);

            // Big burst from sphere
            Vec3 pos = sp.position();
            for (int d = 0; d < 36; d++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI;
                double speed = 0.18 + world.random.nextDouble() * 0.14;
                double vx    = Math.sin(phi) * Math.cos(theta) * speed;
                double vy    = Math.cos(phi) * speed;
                double vz    = Math.sin(phi) * Math.sin(theta) * speed;
                DustParticleOptions col = switch (d % 4) {
                    case 0  -> PURPLE;
                    case 1  -> WHITE;
                    case 2  -> GOLD;
                    default -> BLUE_lIGHT;
                };
                world.sendParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1, vx, vy, vz, 0.0);
            }
            world.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y + 1.0, pos.z, 22, 1.0, 0.8, 1.0, 0.16);
            world.sendParticles(ParticleTypes.FLASH,
                    pos.x, pos.y + 1.0, pos.z, 4, 0.4, 0.3, 0.4, 0);
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;

        // Levitation
        if (progress < 0.80f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.LEVITATION, 10, 1, true, false, false));
        }
        // Blindness
        if (progress > 0.15f && progress < 0.78f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 25, 0, true, false, false));
        }
        // Damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                // Apply Custom Ritual Damage Type
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // BIG star field
        spawnStarField(sp, world, 22);

        // 8 rays rotating around player
        if (t % 2 == 0) {
            int streamCount = 8;
            for (int s = 0; s < streamCount; s++) {
                double angle = s * Math.PI * 2.0 / streamCount + (ticks * 0.07);
                for (int step = 0; step < 8; step++) {
                    double d     = 0.5 + step * 0.75;
                    double h     = 1.0 + step * 0.10;   // slight upward angle on each ray
                    double speed = 0.06 + step * 0.006;
                    DustParticleOptions col = switch (s % 4) {
                        case 0  -> PURPLE;
                        case 1  -> WHITE;
                        case 2  -> BLUE_lIGHT;
                        default -> PINK;
                    };
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * d,
                            pos.y + h,
                            pos.z + Math.sin(angle) * d,
                            1,
                            Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed,
                            0.0);
                }
            }
        }

        // portal around player
        if (t % 3 == 0) {
            world.sendParticles(ParticleTypes.PORTAL,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.8,
                    pos.y + world.random.nextDouble() * 3.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 2.8,
                    2, 0.06, 0.06, 0.06, 0.04);
        }

        // stars around player
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI;
                double speed = 0.10 + world.random.nextDouble() * 0.09;
                world.sendParticles(ParticleTypes.END_ROD,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.6,
                        pos.y + 0.5 + world.random.nextDouble() * 1.8,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.6,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.5,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // purple around player
        if (t % 5 == 0 && progress < 0.70f) {
            world.sendParticles(ParticleTypes.DRAGON_BREATH,
                    pos.x, pos.y + 1.2, pos.z,
                    3, 0.8, 0.5, 0.8, 0.04);
        }

        // calming
        if (progress > 0.78f) {
            for (int i = 0; i < 4; i++) {
                world.sendParticles(PURPLE,
                        pos.x, pos.y + 1.0, pos.z, 1, 0.6, 0.4, 0.6, 0.005);
                world.sendParticles(PURPLE_DARK,
                        pos.x, pos.y + 0.5, pos.z, 1, 0.5, 0.3, 0.5, 0.004);
            }
        }

        if (t % 4 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSLOOP.get(),
                    SoundSource.PLAYERS,  0.6f, 0.7f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        Supplier<PowerInterface> factory =
                SPACE_POWERS.get(sp.getRandom().nextInt(SPACE_POWERS.size()));
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
        for (int d = 0; d < 24; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi   = world.random.nextDouble() * Math.PI;
            double speed = 0.14;
            world.sendParticles(WHITE,
                    pos.x, pos.y + 1.0, pos.z, 1,
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(phi) * Math.sin(theta) * speed,
                    0.0);
        }
        world.sendParticles(PURPLE, pos.x, pos.y + 1.0, pos.z,
                16, 1.4, 1.1, 1.4, 0.09);
        world.sendParticles(PINK,   pos.x, pos.y + 1.0, pos.z,
                12, 1.2, 1.0, 1.2, 0.08);
        world.sendParticles(BLUE,   pos.x, pos.y + 1.0, pos.z,
                10, 1.0, 0.9, 1.0, 0.07);
        world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z,
                8, 0.9, 0.8, 0.9, 0.12);
        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                3, 0.3, 0.2, 0.3, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.1f);
    }
}