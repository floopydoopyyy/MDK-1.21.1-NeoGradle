package com.loopy.loopypowers.client.fx;

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

public class SeveranceRitualClient {

    private static final int STAGE_1_TICKS = 65;
    private static final int STAGE_2_TICKS = 55;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 75;

    // Particles
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.06f, 0.01f, 0.10f), 1.6f);
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.35f, 0.00f, 0.52f), 1.5f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.60f, 0.18f, 0.82f), 1.3f);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.28f, 0.01f, 0.03f), 1.5f);
    private static final DustParticleOptions GRAY = new DustParticleOptions(new Vector3f(0.72f, 0.70f, 0.68f), 0.9f);

    private static final double[] TENDRIL_ANGLES = {
            0.0, Math.PI * 0.42, Math.PI * 0.81, Math.PI, Math.PI * 1.35, Math.PI * 1.78
    };

    public static void tick(Level world, Player player, int ticks) {
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

    private static void tickStage1(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.7f, 0.4f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.GRAVEL_PLACE, SoundSource.PLAYERS, 0.5f, 0.5f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        double maxReach = 5.0;
        double innerEdge = maxReach * (1.0 - progress);

        if (t % 2 == 0) {
            for (double angle : TENDRIL_ANGLES) {
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = innerEdge + (maxReach - innerEdge) * ((double) s / steps);
                    double jitter = (world.random.nextDouble() - 0.5) * 0.22;
                    double perpX = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ = Math.sin(angle + Math.PI * 0.5) * jitter;

                    DustParticleOptions col = (s < 4) ? BLACK : (s < 7) ? PURPLE : VIOLET;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * d + perpX, pos.y + 0.05, pos.z + Math.sin(angle) * d + perpZ, 1, 0.02, 0.01, 0.02, 0.002);
                }

                if (t % 4 == 0 && innerEdge > 0.4) {
                    spawnParticles(world, VIOLET, pos.x + Math.cos(angle) * innerEdge, pos.y + 0.12, pos.z + Math.sin(angle) * innerEdge, 2, 0.05, 0.08, 0.05, 0.018);
                    spawnParticles(world, PURPLE, pos.x + Math.cos(angle) * innerEdge, pos.y + 0.1, pos.z + Math.sin(angle) * innerEdge, 1, 0.02, 0.05, 0.02, 0.010);
                }
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.8 + world.random.nextDouble() * 4.0;
                spawnParticles(world, ParticleTypes.ASH, pos.x + Math.cos(angle) * r, pos.y + 2.0 + world.random.nextDouble() * 2.0, pos.z + Math.sin(angle) * r, 1, 0.02, -0.008, 0.02, 0.003);
            }
        }

        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 0.6;
                spawnParticles(world, BLACK, pos.x + Math.cos(angle) * r, pos.y + 0.05 + world.random.nextDouble() * 1.5 * progress, pos.z + Math.sin(angle) * r, 1, 0.01, 0.03, 0.01, 0.005);
            }
        }

        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 1.0 + world.random.nextDouble() * 2.5;
                spawnParticles(world, PURPLE, pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r, 1, 0.01, 0.04, 0.01, 0.008);
            }
        }

        if (t % 8 == 0 && progress > 0.3f) {
            double angle = TENDRIL_ANGLES[world.random.nextInt(TENDRIL_ANGLES.length)];
            double r = innerEdge + world.random.nextDouble() * 2.0;
            spawnParticles(world, ParticleTypes.WITCH, pos.x + Math.cos(angle) * r, pos.y + 0.5 + world.random.nextDouble() * 0.8, pos.z + Math.sin(angle) * r, 2, 0.06, 0.06, 0.06, 0.02);
        }

        if (t % 22 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.5f, 0.35f + (float) progress * 0.2f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.5f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SOUL_SAND_HIT, SoundSource.PLAYERS, 0.8f, 0.6f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();
        double coilRotation = time * 0.03;

        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = 0.6 + (double) s / steps * 3.9;
                    double jitter = (world.random.nextDouble() - 0.5) * 0.18;
                    double perpX = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ = Math.sin(angle + Math.PI * 0.5) * jitter;

                    DustParticleOptions col = (s < 3) ? BLACK : (s < 6) ? PURPLE : VIOLET;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * d + perpX, pos.y + 0.05, pos.z + Math.sin(angle) * d + perpZ, 1, 0.02, 0.01, 0.02, 0.003);
                }

                double riseHeight = 0.5 + progress * 3.5;
                if (t % 3 == 0) {
                    spawnParticles(world, PURPLE, pos.x + Math.cos(angle) * 0.7, pos.y + world.random.nextDouble() * riseHeight, pos.z + Math.sin(angle) * 0.7, 1, 0.02, 0.04, 0.02, 0.008);
                    if (world.random.nextFloat() < 0.4f) {
                        spawnParticles(world, VIOLET, pos.x + Math.cos(angle) * 0.55, pos.y + riseHeight, pos.z + Math.sin(angle) * 0.55, 1, 0.03, 0.03, 0.03, 0.012);
                    }
                }
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.2 + world.random.nextDouble() * 0.9;
                spawnParticles(world, ParticleTypes.WARPED_SPORE, pos.x + Math.cos(angle) * r, pos.y + 0.5 + world.random.nextDouble() * 2.0, pos.z + Math.sin(angle) * r, 1, 0.01, 0.05, 0.01, 0.012);
            }
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.3 + world.random.nextDouble() * 0.8;
                spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME, pos.x + Math.cos(angle) * r, pos.y + 0.3 + world.random.nextDouble() * 2.2, pos.z + Math.sin(angle) * r, 1, 0.02, 0.04, 0.02, 0.01);
            }
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 5.0;
                spawnParticles(world, ParticleTypes.ASH, pos.x + Math.cos(angle) * r, pos.y + 1.5 + world.random.nextDouble() * 3.0, pos.z + Math.sin(angle) * r, 1, 0.01, -0.01, 0.01, 0.003);
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.4 + world.random.nextDouble() * 1.6;
                spawnParticles(world, PURPLE, pos.x + Math.cos(angle) * r, pos.y + 0.3 + world.random.nextDouble() * 0.8, pos.z + Math.sin(angle) * r, 1, 0.01, 0.02, 0.01, 0.005);
            }
        }

        if (t % 5 == 0) {
            spawnParticles(world, ParticleTypes.WITCH, pos.x + (world.random.nextDouble() - 0.5) * 1.6, pos.y + 0.5 + world.random.nextDouble() * 1.5, pos.z + (world.random.nextDouble() - 0.5) * 1.6, 3, 0.08, 0.08, 0.08, 0.025);
        }

        if (t == 20) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.4f, 0.6f, false);
        if (t == 42) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.5f, 0.55f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.7f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.9f, 0.7f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        long time = world.getGameTime();

        if (t % 2 == 0) {
            int burstCount = (int)(8 + progress * 14);
            for (int i = 0; i < burstCount; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI * 0.6;
                double speed = 0.08 + progress * 0.07;
                DustParticleOptions col = switch (i % 5) {
                    case 0  -> BLACK;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    case 3  -> RED;
                    default -> GRAY;
                };
                spawnParticles(world, col, pos.x, pos.y + 1.1, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed * 0.6 + 0.03, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }
        }

        double coilRotation = time * 0.05;
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                double r = 0.65 * (1.0 - progress * 0.35);
                if (r > 0.2) {
                    DustParticleOptions col = (t % 4 == 0) ? VIOLET : BLACK;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + 0.3, pos.z + Math.sin(angle) * r, 1, 0.02, 0.02, 0.02, 0.006);
                }
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.8;
                spawnParticles(world, RED, pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r, 1, 0.01, 0.01, 0.01, 0.003);
            }
            int coronaPoints = 12;
            double coronaR = 0.9 - progress * 0.2;
            for (int i = 0; i < coronaPoints; i++) {
                double angle = world.getGameTime() * 0.08 + i * Math.PI * 2.0 / coronaPoints;
                spawnParticles(world, VIOLET, pos.x + Math.cos(angle) * coronaR, pos.y + 1.1, pos.z + Math.sin(angle) * coronaR, 1, 0.02, 0.03, 0.02, 0.007);
            }
            spawnParticles(world, ParticleTypes.WITCH, pos.x + (world.random.nextDouble() - 0.5) * 1.4, pos.y + 0.5 + world.random.nextDouble() * 2.0, pos.z + (world.random.nextDouble() - 0.5) * 1.4, 4, 0.10, 0.10, 0.10, 0.03);
        }

        if (t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.0;
                spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME, pos.x + Math.cos(angle) * r, pos.y + 0.2 + world.random.nextDouble() * 2.5, pos.z + Math.sin(angle) * r, 1, 0.02, 0.05, 0.02, 0.016);
            }
        }

        if (t % 14 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, (float)(0.5 + progress * 0.4), 0.55f + (float) progress * 0.35f, false);
        }
        if (t == 28) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f, false);
        }
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.1f, 0.5f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.8f, 0.7f, false);

            for (int d = 0; d < 40; d++) {
                double theta = d * Math.PI * 2.0 / 40;
                double phi = Math.PI * 0.35 + world.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.22 + world.random.nextDouble() * 0.14;
                DustParticleOptions col = switch (d % 4) {
                    case 0  -> PURPLE;
                    case 1  -> BLACK;
                    case 2  -> RED;
                    default -> VIOLET;
                };
                spawnParticles(world, col, pos.x, pos.y + 1.1, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }

            spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 24, 1.1, 0.9, 1.1, 0.14);
            spawnParticles(world, ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z, 16, 0.8, 0.6, 0.8, 0.10);
            spawnParticles(world, ParticleTypes.ASH, pos.x, pos.y + 1.0, pos.z, 35, 1.3, 0.9, 1.3, 0.11);

            int ringPoints = 18;
            for (int i = 0; i < ringPoints; i++) {
                double angle = i * Math.PI * 2.0 / ringPoints;
                double speed = 0.28;
                spawnParticles(world, PURPLE, pos.x + Math.cos(angle) * 0.4, pos.y + 0.2, pos.z + Math.sin(angle) * 0.4, 1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
            }
        }

        double progress = (double) t / STAGE_4_TICKS;

        if (t > 8 && t % 2 == 0) {
            int pullCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < pullCount; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI;
                double srcR = 2.5 + world.random.nextDouble() * 4.5;
                double fromX = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY = pos.y + 1.0 + Math.cos(phi) * srcR * 0.4;
                double fromZ = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3 toward = pos.add(0, 1, 0).subtract(fromX, fromY, fromZ).normalize().scale(0.10 + (1.0 - progress) * 0.06);

                DustParticleOptions col = switch (i % 4) {
                    case 0  -> BLACK;
                    case 1  -> GRAY;
                    case 2  -> PURPLE;
                    default -> RED;
                };
                spawnParticles(world, col, fromX, fromY, fromZ, 1, toward.x, toward.y, toward.z, 0.008);
            }
        }

        if (progress < 0.65f && t % 3 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 0.7;
            spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME, pos.x + Math.cos(angle) * r, pos.y + 0.2 + world.random.nextDouble() * 2.0, pos.z + Math.sin(angle) * r, 1, 0.01, 0.03, 0.01, 0.008);
        }

        if (progress > 0.15f && progress < 0.65f && t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.3 + world.random.nextDouble() * 1.2;
                spawnParticles(world, PURPLE, pos.x + Math.cos(angle) * r, pos.y + 0.2 + world.random.nextDouble() * 2.2, pos.z + Math.sin(angle) * r, 1, 0.01, 0.02, 0.01, 0.006);
            }
        }

        if (t % 3 == 0 && progress < 0.75f) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 4.5;
                spawnParticles(world, ParticleTypes.ASH, pos.x + Math.cos(angle) * r, pos.y + 3.0 + world.random.nextDouble() * 2.0, pos.z + Math.sin(angle) * r, 1, 0.01, -0.015, 0.01, 0.003);
            }
        }

        if (progress > 0.72f) {
            spawnParticles(world, BLACK, pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.003);
            spawnParticles(world, GRAY, pos.x, pos.y + 0.8, pos.z, 1, 0.3, 0.2, 0.3, 0.002);
            spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 1, 0.3, 0.3, 0.3, 0.002);
        }

        if (t % 8 == 0 && progress < 0.60f) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, (float)(0.4 + (1.0 - progress) * 0.3), 0.45f + (float) progress * 0.3f, false);
        }
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        spawnParticles(world, BLACK, pos.x, pos.y + 1.0, pos.z, 20, 1.3, 1.0, 1.3, 0.07);
        spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 16, 1.1, 0.9, 1.1, 0.06);
        spawnParticles(world, VIOLET, pos.x, pos.y + 1.0, pos.z, 10, 0.9, 0.8, 0.9, 0.05);
        spawnParticles(world, GRAY, pos.x, pos.y + 1.0, pos.z, 12, 1.0, 0.8, 1.0, 0.05);
        spawnParticles(world, ParticleTypes.ASH, pos.x, pos.y + 1.0, pos.z, 22, 1.5, 1.0, 1.5, 0.06);
        spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 8, 0.6, 0.5, 0.6, 0.05);
        spawnParticles(world, ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z, 6, 0.5, 0.4, 0.5, 0.06);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.8f, 1.2f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SOUL_SAND_HIT, SoundSource.PLAYERS, 0.6f, 0.5f, false);
    }
}