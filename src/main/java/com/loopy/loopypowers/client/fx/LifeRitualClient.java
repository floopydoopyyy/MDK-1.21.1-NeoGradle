package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class LifeRitualClient {

    private static final int STAGE_1_TICKS = 75;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;

    private static final double CANOPY_HEIGHT = 11.0;
    private static final double ROOT_REACH    = 5.5;

    // Particles
    private static final DustParticleOptions DARK_GREEN = new DustParticleOptions(new Vector3f(0.08f, 0.55f, 0.12f), 1.5f);
    private static final DustParticleOptions LIGHT_GREEN = new DustParticleOptions(new Vector3f(0.35f, 0.90f, 0.22f), 1.3f);
    private static final DustParticleOptions ROOT = new DustParticleOptions(new Vector3f(0.22f, 0.32f, 0.08f), 1.6f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(0.88f, 0.82f, 0.10f), 1.2f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(0.98f, 0.92f, 0.90f), 1.0f);

    // Geometry Constants
    private static final double[] ROOT_ANGLES = {
            0.0, Math.PI * 0.35, Math.PI * 0.72, Math.PI, Math.PI * 1.32, Math.PI * 1.74
    };
    private static final double[][] ROOT_BRANCHES = {
            { +0.40, -0.28 }, { -0.35, +0.30 }, { +0.32, -0.38 }, { -0.30, +0.42 }, { +0.38, -0.25 }, { -0.42, +0.32 },
    };
    private static final double BRANCH_START = 0.45;

    public static void tick(Level world, Player player, int ticks) {
        if (ticks == 1 || ticks % 28 == 0) {
            world.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    ModSounds.RITUALLOOP.get(), SoundSource.PLAYERS, 0.5f, 0.7f, false);
        }

        int stage = currentStage(ticks);
        int stageTick = stageLocalTick(ticks, stage);

        switch (stage) {
            case 1 -> tickStage1(world, player, stageTick);
            case 2 -> tickStage2(world, player, stageTick);
            case 3 -> tickStage3(world, player, stageTick);
            case 4 -> tickStage4(world, player, stageTick);
        }

        if (ticks >= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS) {
            onComplete(world, player);
        }
    }

    private static int currentStage(int ticks) {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) return 3;
        return 4;
    }

    private static int stageLocalTick(int ticks, int stage) {
        return switch (stage) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    private static void spawnParticles(Level world, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        RandomSource random = world.random;
        if (count == 0) {
            world.addParticle(particle, x, y, z, dx * speed, dy * speed, dz * speed);
        } else {
            for (int i = 0; i < count; i++) {
                double px = x + random.nextGaussian() * dx;
                double py = y + random.nextGaussian() * dy;
                double pz = z + random.nextGaussian() * dz;
                double vx = random.nextGaussian() * speed;
                double vy = random.nextGaussian() * speed;
                double vz = random.nextGaussian() * speed;
                world.addParticle(particle, px, py, pz, vx, vy, vz);
            }
        }
    }

    private static void spawnRoots(Level world, Vec3 pos, double reach, boolean glow) {
        for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
            double angle = ROOT_ANGLES[ri];
            int steps = Math.max(3, (int)(reach / 0.5));

            for (int s = 0; s < steps; s++) {
                double d = 0.3 + (double) s / steps * reach;
                double jitter = (world.random.nextDouble() - 0.5) * 0.20;
                double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;

                DustParticleOptions col = (s < steps / 3) ? ROOT : DARK_GREEN;
                spawnParticles(world, col, pos.x + Math.cos(angle) * d + perpX, pos.y + 0.04, pos.z + Math.sin(angle) * d + perpZ, 1, 0.01, 0.01, 0.01, 0.002);
            }

            if (glow && reach > 1.0) {
                spawnParticles(world, LIGHT_GREEN, pos.x + Math.cos(angle) * reach, pos.y + 0.09, pos.z + Math.sin(angle) * reach, 1, 0.04, 0.05, 0.04, 0.012);
            }

            double branchStartD = reach * BRANCH_START;
            double[] branches   = ROOT_BRANCHES[ri];
            double   branchLen  = reach * (1.0 - BRANCH_START) * 0.75;

            if (branchStartD > 0.4 && branchLen > 0.3) {
                for (double branchOffset : branches) {
                    double branchAngle = angle + branchOffset;
                    int    bSteps      = Math.max(2, (int)(branchLen / 0.45));

                    for (int s = 0; s < bSteps; s++) {
                        double d      = branchStartD + (double) s / bSteps * branchLen;
                        double jitter = (world.random.nextDouble() - 0.5) * 0.16;
                        double bPerpX = Math.cos(branchAngle + Math.PI * 0.5) * jitter;
                        double bPerpZ = Math.sin(branchAngle + Math.PI * 0.5) * jitter;

                        spawnParticles(world, DARK_GREEN, pos.x + Math.cos(branchAngle) * d + bPerpX, pos.y + 0.04, pos.z + Math.sin(branchAngle) * d + bPerpZ, 1, 0.01, 0.01, 0.01, 0.002);
                    }

                    if (glow) {
                        spawnParticles(world, LIGHT_GREEN, pos.x + Math.cos(branchAngle) * (branchStartD + branchLen), pos.y + 0.08, pos.z + Math.sin(branchAngle) * (branchStartD + branchLen), 1, 0.03, 0.04, 0.03, 0.010);
                    }
                }
            }
        }
    }

    private static void tickStage1(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 0.9f, 0.75f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.6f, 0.85f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        double reach = 0.6 + progress * (ROOT_REACH - 0.6);
        if (t % 3 == 0) spawnRoots(world, pos, reach, t % 8 == 0);

        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double rAngle = ROOT_ANGLES[world.random.nextInt(ROOT_ANGLES.length)];
                double d = 0.6 + world.random.nextDouble() * reach * 0.8;
                spawnParticles(world, ParticleTypes.SPORE_BLOSSOM_AIR, pos.x + Math.cos(rAngle) * d, pos.y + 0.1, pos.z + Math.sin(rAngle) * d, 1, 0.04, 0.06, 0.04, 0.015);
            }
        }

        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.5 + world.random.nextDouble() * 4.0;
                spawnParticles(world, ParticleTypes.FALLING_SPORE_BLOSSOM, pos.x + Math.cos(angle) * r, pos.y + 5.0 + world.random.nextDouble() * 4.0, pos.z + Math.sin(angle) * r, 1, 0.02, -0.04, 0.02, 0.005);
            }
        }

        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 0.5;
                spawnParticles(world, GOLD, pos.x + Math.cos(angle) * r, pos.y + 0.05 + world.random.nextDouble() * 1.2 * progress, pos.z + Math.sin(angle) * r, 1, 0.01, 0.03, 0.01, 0.006);
            }
        }

        if (t % 24 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.5f, 0.8f + (float) progress * 0.25f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.8f, 0.75f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.6f, 0.65f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();

        if (t % 3 == 0) spawnRoots(world, pos, ROOT_REACH, t % 6 == 0);

        double vineHeight = CANOPY_HEIGHT * progress;
        if (t % 2 == 0) {
            for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
                double baseAngle = ROOT_ANGLES[ri];
                double cx = pos.x + Math.cos(baseAngle) * 0.8;
                double cz = pos.z + Math.sin(baseAngle) * 0.8;
                int vineSteps = Math.max(4, (int)(vineHeight / 0.45));

                for (int s = 0; s < vineSteps; s++) {
                    double fraction = (double) s / vineSteps;
                    double y = pos.y + fraction * vineHeight;
                    double vR = 0.55 * (1.0 - fraction * 0.5);
                    double twist = baseAngle + fraction * Math.PI * 3.0 + time * 0.025;

                    DustParticleOptions col = (fraction < 0.4) ? ROOT : (fraction < 0.75) ? DARK_GREEN : LIGHT_GREEN;
                    spawnParticles(world, col, cx + Math.cos(twist) * vR, y, cz + Math.sin(twist) * vR, 1, 0.02, 0.02, 0.02, 0.004);
                }

                if (vineHeight > 1.5 && t % 4 == 0) {
                    spawnParticles(world, LIGHT_GREEN, cx + (world.random.nextDouble() - 0.5) * 0.3, pos.y + vineHeight, cz + (world.random.nextDouble() - 0.5) * 0.3, 1, 0.04, 0.06, 0.04, 0.015);
                }
            }
        }

        double canopyY = pos.y + CANOPY_HEIGHT;
        double canopyR = 3.5 - progress * 1.8;
        int canopyDensity = (int)(6 + progress * 20);

        if (t % 2 == 0) {
            for (int i = 0; i < canopyDensity; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * canopyR;
                double h = (world.random.nextDouble() - 0.3) * 1.8;
                DustParticleOptions col = world.random.nextFloat() < 0.55f ? PINK : GOLD;
                spawnParticles(world, col, pos.x + Math.cos(angle) * r, canopyY + h, pos.z + Math.sin(angle) * r, 1, 0.03, 0.04, 0.03, 0.010);
            }
        }

        if (t % 5 == 0) spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * canopyR * 1.4, canopyY, pos.z + (world.random.nextDouble() - 0.5) * canopyR * 1.4, 1, 0, 0.03, 0, 0.02);

        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 2.5;
                double h = 2.0 + world.random.nextDouble() * (CANOPY_HEIGHT - 3.0);
                spawnParticles(world, ParticleTypes.CHERRY_LEAVES, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0.02, -0.03, 0.02, 0.008);
            }
        }

        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.5;
                spawnParticles(world, ParticleTypes.SPORE_BLOSSOM_AIR, pos.x + Math.cos(angle) * r, pos.y + 0.2 + world.random.nextDouble() * 3.0, pos.z + Math.sin(angle) * r, 1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        if (t % 20 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.PLAYERS, 0.5f, 0.85f + (float) progress * 0.35f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.6f, 0.65f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.5f, 0.55f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        double canopyR = 1.7 * (1.0 - progress);
        double beamTop = pos.y + CANOPY_HEIGHT;

        if (canopyR > 0.15 && t % 2 == 0) {
            int density = (int)(20 + (1.0 - progress) * 15);
            for (int i = 0; i < density; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * canopyR;
                DustParticleOptions col = world.random.nextFloat() < 0.5f ? PINK : GOLD;
                spawnParticles(world, col, pos.x + Math.cos(angle) * r, beamTop + (world.random.nextDouble() - 0.3), pos.z + Math.sin(angle) * r, 1, 0.02, 0.04, 0.02, 0.012);
            }
        }

        if (t % 2 == 0) {
            int beamSteps = (int)(8 + progress * 10);
            for (int s = 0; s < beamSteps; s++) {
                double fraction = (double) s / beamSteps;
                double y = beamTop - fraction * (CANOPY_HEIGHT - 1.8);
                double jitter = 0.55 * (1.0 - fraction) + 0.04;

                DustParticleOptions col = (fraction < 0.4) ? PINK : (fraction < 0.75) ? LIGHT_GREEN : GOLD;
                spawnParticles(world, col, pos.x + (world.random.nextDouble() - 0.5) * jitter, y, pos.z + (world.random.nextDouble() - 0.5) * jitter, 1, 0, -0.04, 0, 0.008);
            }
        }

        if (t % 3 == 0) spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 0.3, beamTop, pos.z + (world.random.nextDouble() - 0.5) * 0.3, 1, 0, -0.18, 0, 0.0);
        if (t % 4 == 0) spawnRoots(world, pos, ROOT_REACH, true);

        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.8 + world.random.nextDouble() * 2.2;
                double h = 1.0 + world.random.nextDouble() * (CANOPY_HEIGHT - 2.0);
                spawnParticles(world, ParticleTypes.CHERRY_LEAVES, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0.02, -0.04, 0.02, 0.010);
            }
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.5;
                double h = 1.5 + world.random.nextDouble() * 5.0;
                spawnParticles(world, ParticleTypes.SPORE_BLOSSOM_AIR, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0.02, -0.02, 0.02, 0.008);
            }
        }

        if (progress > 0.88f && t % 4 == 0) {
            spawnParticles(world, PINK, pos.x, pos.y + 1.5, pos.z, 6, 0.4, 0.3, 0.4, 0.04);
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.5, pos.z, 1, 0, 0, 0, 0);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.6f, 1.3f, false);
        }

        if (t % 18 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.6f, 0.9f + (float) progress * 0.4f, false);
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.70f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.PLAYERS, 1.0f, 0.55f, false);

            for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
                double angle = ROOT_ANGLES[ri];
                for (double d = 0.3; d <= ROOT_REACH; d += 0.5) {
                    spawnParticles(world, DARK_GREEN, pos.x + Math.cos(angle) * d, pos.y + 0.05, pos.z + Math.sin(angle) * d, 1, Math.cos(angle) * 0.04, 0.14, Math.sin(angle) * 0.04, 0.0);
                }
                double[] branches = ROOT_BRANCHES[ri];
                for (double branchOff : branches) {
                    double bAngle = angle + branchOff;
                    double bStart = ROOT_REACH * BRANCH_START;
                    double bLen = ROOT_REACH * (1.0 - BRANCH_START) * 0.75;
                    for (double d = bStart; d <= bStart + bLen; d += 0.5) {
                        spawnParticles(world, LIGHT_GREEN, pos.x + Math.cos(bAngle) * d, pos.y + 0.05, pos.z + Math.sin(bAngle) * d, 1, Math.cos(bAngle) * 0.04, 0.12, Math.sin(bAngle) * 0.04, 0.0);
                    }
                }
            }

            for (int d = 0; d < 24; d++) {
                double theta = d * Math.PI * 2.0 / 24;
                double phi = Math.PI * 0.35 + world.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.18 + world.random.nextDouble() * 0.10;
                DustParticleOptions col = (d % 2 == 0) ? PINK : GOLD;
                spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }

            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.2, 0.2, 0.2, 0);
        }

        double progress = (double) t / STAGE_4_TICKS;

        int burstCount = (int)(10 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.8;
            double h = world.random.nextDouble() * 2.6;
            DustParticleOptions col = switch (i % 4) {
                case 0  -> DARK_GREEN;
                case 1  -> LIGHT_GREEN;
                case 2  -> GOLD;
                default -> PINK;
            };
            spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0, 0.025, 0, 0.015);
        }

        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 3.0;
                spawnParticles(world, ParticleTypes.CHERRY_LEAVES, pos.x + Math.cos(angle) * r, pos.y + 0.3 + world.random.nextDouble() * 3.5, pos.z + Math.sin(angle) * r, 1, 0.02, 0.03, 0.02, 0.01);
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.5;
                spawnParticles(world, ParticleTypes.SPORE_BLOSSOM_AIR, pos.x + Math.cos(angle) * r, pos.y + 0.2 + world.random.nextDouble() * 2.5, pos.z + Math.sin(angle) * r, 1, 0.02, 0.05, 0.02, 0.015);
            }
        }

        if (t % 5 == 0) spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 1.6, pos.y + 0.5 + world.random.nextDouble() * 2.2, pos.z + (world.random.nextDouble() - 0.5) * 1.6, 1, 0.03, 0.07, 0.03, 0.025);

        if (progress > 0.78f) {
            spawnParticles(world, PINK, pos.x, pos.y + 1.3, pos.z, 2, 0.5, 0.3, 0.5, 0.005);
            spawnParticles(world, GOLD, pos.x, pos.y + 1.5, pos.z, 1, 0.4, 0.2, 0.4, 0.004);
        }

        if (t % 5 == 0) world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSLOOP.get(), SoundSource.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f, false);
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        for (double angle : ROOT_ANGLES) {
            spawnParticles(world, LIGHT_GREEN, pos.x + Math.cos(angle) * 2.0, pos.y + 0.05, pos.z + Math.sin(angle) * 2.0, 3, Math.cos(angle) * 0.03, 0.10, Math.sin(angle) * 0.03, 0.0);
        }

        spawnParticles(world, DARK_GREEN, pos.x, pos.y + 1.0, pos.z, 22, 1.1, 0.9, 1.1, 0.08);
        spawnParticles(world, LIGHT_GREEN, pos.x, pos.y + 1.0, pos.z, 18, 0.9, 0.8, 0.9, 0.07);
        spawnParticles(world, PINK, pos.x, pos.y + 1.0, pos.z, 16, 0.8, 0.7, 0.8, 0.07);
        spawnParticles(world, GOLD, pos.x, pos.y + 1.2, pos.z, 12, 0.6, 0.5, 0.6, 0.06);
        spawnParticles(world, ParticleTypes.CHERRY_LEAVES, pos.x, pos.y + 1.0, pos.z, 20, 1.2, 1.0, 1.2, 0.06);
        spawnParticles(world, ParticleTypes.SPORE_BLOSSOM_AIR, pos.x, pos.y + 0.5, pos.z, 10, 0.8, 0.6, 0.8, 0.04);
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.1, 0.2, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.9f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.PLAYERS, 0.8f, 1.1f, false);
    }
}