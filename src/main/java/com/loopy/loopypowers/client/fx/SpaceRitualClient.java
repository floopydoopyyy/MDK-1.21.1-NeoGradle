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

public class SpaceRitualClient {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    private static final double SINGULARITY_HEIGHT = 3.8;
    private static final double ARM_A = 0.35;
    private static final double ARM_B = 0.50;
    private static final double ARM_T_MAX = 2.7 * Math.PI;
    private static final int ARM_STEPS = 30;

    // Particles
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.52f, 0.08f, 0.80f), 1.4f);
    private static final DustParticleOptions PURPLE_DARK = new DustParticleOptions(new Vector3f(0.20f, 0.02f, 0.38f), 1.6f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(0.88f, 0.40f, 0.94f), 1.2f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.00f, 0.92f, 0.58f), 0.9f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.92f, 0.96f, 1.00f), 0.7f);
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.22f, 0.82f, 0.90f), 1.1f);
    private static final DustParticleOptions BLUE_lIGHT = new DustParticleOptions(new Vector3f(0.78f, 0.92f, 1.00f), 1.3f);

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

    private static void spawnStarField(Level world, Player player, int starsPerTick) {
        Vec3 pos = player.position();
        for (int i = 0; i < starsPerTick; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = 5.5 + world.random.nextDouble() * 6.5;
            double h = -0.5 + world.random.nextDouble() * 13.0;

            double sx = pos.x + Math.cos(angle) * r;
            double sy = pos.y + h;
            double sz = pos.z + Math.sin(angle) * r;

            if (world.random.nextInt(5) == 0) {
                spawnParticles(world, ParticleTypes.END_ROD, sx, sy, sz, 1, 0, 0, 0, 0);
            } else {
                DustParticleOptions star = (world.random.nextInt(3) == 0) ? GOLD : WHITE;
                spawnParticles(world, star, sx, sy, sz, 1, 0.005, 0.005, 0.005, 0.001);
            }
        }
    }

    private static void spawnArmsFlat(Level world, Vec3 pos, double tMax, double armY, double cloudWidth, double rotation, boolean dim) {
        int steps = Math.max(3, (int)(ARM_STEPS * (tMax / ARM_T_MAX)));

        for (int arm = 0; arm < 2; arm++) {
            double armOffset = arm * Math.PI;

            for (int s = 0; s < steps; s++) {
                double theta = (double) s / steps * tMax;
                double r = ARM_A + ARM_B * theta;
                double angle = theta + armOffset + rotation;
                double cloud = cloudWidth * (0.25 + (r / 4.6) * 0.75);

                double px = pos.x + Math.cos(angle) * r + (world.random.nextDouble() - 0.5) * cloud;
                double pz = pos.z + Math.sin(angle) * r + (world.random.nextDouble() - 0.5) * cloud;

                DustParticleOptions col;
                if (dim) {
                    col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                } else {
                    double frac = (double) s / steps;
                    col = (frac < 0.35) ? PURPLE_DARK : (frac < 0.70) ? PURPLE : (s % 2 == 0) ? PINK : BLUE;
                }

                spawnParticles(world, col, px, armY, pz, 1, 0.01, 0.01, 0.01, 0.002);
            }
        }
    }

    private static void tickStage1(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.7f, 0.35f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();
        double rotation = time * 0.018;

        spawnStarField(world, player, (int)(4 + progress * 8));

        double armTMax = ARM_T_MAX * progress;
        if (armTMax > 0.4 && t % 2 == 0) {
            spawnArmsFlat(world, pos, armTMax, pos.y + 0.08, 0.35, rotation, false);
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.3 + world.random.nextDouble() * 1.5;
                spawnParticles(world, PURPLE_DARK, pos.x + Math.cos(angle) * r, pos.y + 0.1 + world.random.nextDouble() * 2.5 * progress, pos.z + Math.sin(angle) * r, 1, 0.01, 0.03, 0.01, 0.006);
            }
        }

        if (t % 14 == 0) spawnParticles(world, ParticleTypes.ENCHANT, pos.x, pos.y + 1.0, pos.z, 6, 0.5, 0.5, 0.5, 1.2);

        if (t % 24 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.4f, 0.28f + (float) progress * 0.35f, false);
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.7f, 0.45f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();
        double rotation = time * 0.018;

        spawnStarField(world, player, 12);

        if (t % 2 == 0) {
            for (int arm = 0; arm < 2; arm++) {
                double armOffset = arm * Math.PI;

                for (int s = 0; s < ARM_STEPS; s++) {
                    double theta = (double) s / ARM_STEPS * ARM_T_MAX;
                    double r = ARM_A + ARM_B * theta;
                    double angle = theta + armOffset + rotation;
                    double armY = pos.y + 0.1 + theta * (0.48 * progress);
                    double cloudW = 0.25 + (r / 4.6) * 0.85;

                    double px = pos.x + Math.cos(angle) * r + (world.random.nextDouble() - 0.5) * cloudW;
                    double py = armY + (world.random.nextDouble() - 0.5) * cloudW * 0.45;
                    double pz = pos.z + Math.sin(angle) * r + (world.random.nextDouble() - 0.5) * cloudW;

                    double frac = (double) s / ARM_STEPS;
                    DustParticleOptions col;
                    if (frac < 0.30) {
                        col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                    } else if (frac < 0.65) {
                        col = (s % 2 == 0) ? PURPLE : PINK;
                    } else {
                        col = (s % 2 == 0) ? PINK : BLUE;
                    }
                    spawnParticles(world, col, px, py, pz, 1, 0.01, 0.02, 0.01, 0.003);
                }
            }
        }

        double orbitR = 2.7;
        double orbitSpeed = time * 0.09;
        double orbitTilt = 0.32;
        int orbitPts = 18;
        if (t % 2 == 0) {
            for (int i = 0; i < orbitPts; i++) {
                double theta = orbitSpeed + i * Math.PI * 2.0 / orbitPts;
                double ox = pos.x + Math.cos(theta) * orbitR;
                double oy = pos.y + 1.6 + Math.sin(theta) * orbitR * Math.sin(orbitTilt);
                double oz = pos.z + Math.sin(theta) * orbitR * Math.cos(orbitTilt);
                DustParticleOptions col = (i % 3 == 0) ? GOLD : BLUE_lIGHT;
                spawnParticles(world, col, ox, oy, oz, 1, 0.01, 0.01, 0.01, 0.004);
            }
        }

        if (t % 6 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 1.0 + world.random.nextDouble() * 3.5;
                double h = 0.4 + world.random.nextDouble() * (3.0 + progress * 2.0);
                spawnParticles(world, ParticleTypes.DRAGON_BREATH, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0.02, 0.01, 0.02, 0.005);
            }
        }

        if (t == 18) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.5f, 0.65f, false);
        if (t == 42) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.5f, 0.45f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.65f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        long time = world.getGameTime();
        double rotation = time * 0.018;

        double singX = pos.x;
        double singY = pos.y + SINGULARITY_HEIGHT;
        double singZ = pos.z;

        spawnStarField(world, player, 16);

        double armShrink = 1.0 - progress * 0.50;
        if (t % 3 == 0) {
            spawnArmsFlat(world, pos, ARM_T_MAX * armShrink, pos.y + 0.08, 0.55, rotation, true);
        }

        double discR = 1.9 - progress * 0.5;
        double discSpeed = time * (0.14 + progress * 0.18);
        int discPts = 24;
        if (t % 2 == 0) {
            for (int i = 0; i < discPts; i++) {
                double theta = discSpeed + i * Math.PI * 2.0 / discPts;
                double dx = pos.x + Math.cos(theta) * discR;
                double dz = pos.z + Math.sin(theta) * discR;

                DustParticleOptions col = switch (i % 4) {
                    case 0  -> BLUE_lIGHT;
                    case 1  -> WHITE;
                    case 2  -> PURPLE;
                    default -> BLUE;
                };
                spawnParticles(world, col, dx, singY - 0.1, dz, 1, 0.03, 0.02, 0.03, 0.006);

                if (i % 3 == 0) {
                    spawnParticles(world, BLUE_lIGHT, pos.x + Math.cos(theta + 0.2) * (discR * 0.58), singY + 0.12, pos.z + Math.sin(theta + 0.2) * (discR * 0.58), 1, 0.02, 0.02, 0.02, 0.005);
                }
            }
        }

        if (t % 2 == 0) {
            spawnParticles(world, ParticleTypes.PORTAL, singX, singY, singZ, 8, 0.28, 0.18, 0.28, 0.06);
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                spawnParticles(world, ParticleTypes.REVERSE_PORTAL, singX + (world.random.nextDouble() - 0.5) * 0.2, singY, singZ + (world.random.nextDouble() - 0.5) * 0.2, 1, 0.05, 0.12, 0.05, 0.04);
            }
        }

        int pullCount = (int)(8 + progress * 16);
        if (t % 2 == 0) {
            for (int i = 0; i < pullCount; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI;
                double srcR = 4.5 + world.random.nextDouble() * 5.5;
                double fromX = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY = pos.y + 1.0 + Math.cos(phi) * srcR * 0.5 + 2.0;
                double fromZ = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3 toward = new Vec3(singX, singY, singZ).subtract(fromX, fromY, fromZ).normalize().scale(0.09 + progress * 0.07);

                DustParticleOptions col = switch (world.random.nextInt(4)) {
                    case 0  -> WHITE;
                    case 1  -> PURPLE;
                    case 2  -> BLUE_lIGHT;
                    default -> GOLD;
                };
                spawnParticles(world, col, fromX, fromY, fromZ, 1, toward.x, toward.y, toward.z, 0.010);
            }
        }

        if (t % 12 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, (float)(0.5 + progress * 0.5), 0.65f + (float) progress * 0.45f, false);
        if (t == 32) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.3f, false);
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9f, 0.55f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 0.65f, false);

            for (int d = 0; d < 36; d++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI;
                double speed = 0.18 + world.random.nextDouble() * 0.14;
                double vx = Math.sin(phi) * Math.cos(theta) * speed;
                double vy = Math.cos(phi) * speed;
                double vz = Math.sin(phi) * Math.sin(theta) * speed;
                DustParticleOptions col = switch (d % 4) {
                    case 0  -> PURPLE;
                    case 1  -> WHITE;
                    case 2  -> GOLD;
                    default -> BLUE_lIGHT;
                };
                spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 1, vx, vy, vz, 0.0);
            }
            spawnParticles(world, ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 22, 1.0, 0.8, 1.0, 0.16);
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 4, 0.4, 0.3, 0.4, 0);
        }

        double progress = (double) t / STAGE_4_TICKS;

        spawnStarField(world, player, 22);

        if (t % 2 == 0) {
            int streamCount = 8;
            for (int s = 0; s < streamCount; s++) {
                double angle = s * Math.PI * 2.0 / streamCount + (t * 0.07);
                for (int step = 0; step < 8; step++) {
                    double d = 0.5 + step * 0.75;
                    double h = 1.0 + step * 0.10;
                    double speed = 0.06 + step * 0.006;
                    DustParticleOptions col = switch (s % 4) {
                        case 0  -> PURPLE;
                        case 1  -> WHITE;
                        case 2  -> BLUE_lIGHT;
                        default -> PINK;
                    };
                    spawnParticles(world, col, pos.x + Math.cos(angle) * d, pos.y + h, pos.z + Math.sin(angle) * d, 1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
                }
            }
        }

        if (t % 3 == 0) {
            spawnParticles(world, ParticleTypes.PORTAL, pos.x + (world.random.nextDouble() - 0.5) * 2.8, pos.y + world.random.nextDouble() * 3.0, pos.z + (world.random.nextDouble() - 0.5) * 2.8, 2, 0.06, 0.06, 0.06, 0.04);
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI;
                double speed = 0.10 + world.random.nextDouble() * 0.09;
                spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 0.6, pos.y + 0.5 + world.random.nextDouble() * 1.8, pos.z + (world.random.nextDouble() - 0.5) * 0.6, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed * 0.5, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }
        }

        if (t % 5 == 0 && progress < 0.70f) {
            spawnParticles(world, ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.2, pos.z, 3, 0.8, 0.5, 0.8, 0.04);
        }

        if (progress > 0.78f) {
            for (int i = 0; i < 4; i++) {
                spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 1, 0.6, 0.4, 0.6, 0.005);
                spawnParticles(world, PURPLE_DARK, pos.x, pos.y + 0.5, pos.z, 1, 0.5, 0.3, 0.5, 0.004);
            }
        }

        if (t % 4 == 0) world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSLOOP.get(), SoundSource.PLAYERS, 0.6f, 0.7f, false);
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        for (int d = 0; d < 24; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi = world.random.nextDouble() * Math.PI;
            double speed = 0.14;
            spawnParticles(world, WHITE, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
        }
        spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 16, 1.4, 1.1, 1.4, 0.09);
        spawnParticles(world, PINK, pos.x, pos.y + 1.0, pos.z, 12, 1.2, 1.0, 1.2, 0.08);
        spawnParticles(world, BLUE, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.9, 1.0, 0.07);
        spawnParticles(world, ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 8, 0.9, 0.8, 0.9, 0.12);
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.3, 0.2, 0.3, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.1f, false);
    }
}