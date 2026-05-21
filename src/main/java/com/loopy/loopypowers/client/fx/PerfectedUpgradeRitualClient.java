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

public class PerfectedUpgradeRitualClient {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;

    // Particles
    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(0.95f, 0.15f, 0.70f), 1.5f);
    private static final DustParticleOptions PINK_WHITE = new DustParticleOptions(new Vector3f(1.00f, 0.62f, 0.88f), 1.2f);
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.52f, 0.08f, 0.80f), 1.5f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.75f, 0.35f, 0.95f), 1.3f);
    private static final DustParticleOptions WHITE_YELLOW = new DustParticleOptions(new Vector3f(0.96f, 0.94f, 1.00f), 1.3f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.00f, 0.98f, 1.00f), 1.8f);

    private static final double[] SIGIL_ANGLES = {
            0.0, Math.PI * 0.28, Math.PI * 0.55, Math.PI * 0.82, Math.PI, Math.PI * 1.25, Math.PI * 1.55, Math.PI * 1.80
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

    private static void spawnFallingColumn(Level world, Vec3 pos, int count, double radius, double minH, double maxH) {
        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * radius;
            DustParticleOptions col = world.random.nextBoolean() ? MAGENTA : WHITE_YELLOW;
            spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + minH + world.random.nextDouble() * (maxH - minH), pos.z + Math.sin(angle) * r, 1, 0.02, -0.10, 0.02, 0.004);
        }
    }

    private static void spawnHalo(Level world, Vec3 pos, double radius, double height, double speed, int points, DustParticleOptions primary, DustParticleOptions secondary) {
        for (int i = 0; i < points; i++) {
            double angle = speed + i * Math.PI * 2.0 / points;
            DustParticleOptions col = (i % 2 == 0) ? primary : secondary;
            spawnParticles(world, col, pos.x + Math.cos(angle) * radius, pos.y + height, pos.z + Math.sin(angle) * radius, 1, 0.01, 0.015, 0.01, 0.005);
        }
    }

    private static void tickStage1(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.45f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 0.25f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();
        double sigRotation = time * 0.020;
        double spokeReach = 1.0 + progress * 4.0;

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + (double) s / steps * spokeReach;
                    DustParticleOptions col = (s < 4) ? PURPLE : (s < 7) ? VIOLET : PINK_WHITE;
                    double jitter = (world.random.nextDouble() - 0.5) * 0.14;
                    double perpX = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ = Math.sin(angle + Math.PI * 0.5) * jitter;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * d + perpX, pos.y + 0.05, pos.z + Math.sin(angle) * d + perpZ, 1, 0.01, 0.01, 0.01, 0.003);
                }

                if (t % 5 == 0 && spokeReach > 1.0) {
                    spawnParticles(world, MAGENTA, pos.x + Math.cos(angle) * spokeReach, pos.y + 0.10, pos.z + Math.sin(angle) * spokeReach, 1, 0.04, 0.06, 0.04, 0.014);
                }
            }

            if (spokeReach > 1.5) {
                int ringPoints = 32;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                    DustParticleOptions col = (i % 3 == 0) ? MAGENTA : PURPLE;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * spokeReach, pos.y + 0.06, pos.z + Math.sin(angle) * spokeReach, 1, 0.01, 0.01, 0.01, 0.003);
                }
            }
        }

        if (t % 2 == 0) spawnFallingColumn(world, pos, (int)(3 + progress * 7), 1.4, 7.0, 13.0);

        if (t % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.5 + world.random.nextDouble() * spokeReach * 0.7;
                spawnParticles(world, VIOLET, pos.x + Math.cos(angle) * r, pos.y + 0.1 + world.random.nextDouble() * 2.5 * progress, pos.z + Math.sin(angle) * r, 1, 0.01, 0.04, 0.01, 0.008);
            }
        }

        if (t % 14 == 0) spawnParticles(world, ParticleTypes.ENCHANT, pos.x, pos.y + 1.0, pos.z, 12, 0.8, 0.8, 0.8, 1.6);

        if (t % 7 == 0 && progress > 0.25f) {
            double spokeAngle = SIGIL_ANGLES[world.random.nextInt(SIGIL_ANGLES.length)] + sigRotation;
            spawnParticles(world, ParticleTypes.GLOW, pos.x + Math.cos(spokeAngle) * spokeReach, pos.y + 0.1, pos.z + Math.sin(spokeAngle) * spokeReach, 2, 0.05, 0.05, 0.05, 0);
        }

        if (t % 18 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 0.35f + (float) progress * 0.45f, false);
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.9f, 0.65f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 0.60f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();
        double sigRotation = time * 0.035;
        double sigilY = pos.y + progress * 1.2;

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int steps = 9;
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + (double) s / steps * 5.0;
                    DustParticleOptions col = (s < 3) ? PURPLE : (s < 6) ? VIOLET : MAGENTA;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * d, sigilY, pos.z + Math.sin(angle) * d, 1, 0.02, 0.015, 0.02, 0.003);
                }
            }

            int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                DustParticleOptions col = (i % 2 == 0) ? PURPLE : PINK_WHITE;
                spawnParticles(world, col, pos.x + Math.cos(angle) * 5.0, sigilY, pos.z + Math.sin(angle) * 5.0, 1, 0.01, 0.01, 0.01, 0.003);
            }
        }

        if (t % 2 == 0) spawnHalo(world, pos, 2.4, 1.6, time * 0.07, 24, MAGENTA, VIOLET);
        if (t % 2 == 0) spawnHalo(world, pos, 1.7, 1.9, -time * 0.10, 18, PURPLE, WHITE_YELLOW);
        if (t % 3 == 0) spawnHalo(world, pos, 1.0, 2.2, time * 0.14, 12, WHITE_YELLOW, VIOLET);

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                double cx = pos.x + Math.cos(angle) * 0.7;
                double cz = pos.z + Math.sin(angle) * 0.7;
                int steps = (int)(5 + progress * 7);
                for (int s = 0; s < steps; s++) {
                    double h = pos.y + 0.3 + s * (0.6 + progress * 0.4);
                    DustParticleOptions col = switch (s % 3) {
                        case 0  -> MAGENTA;
                        case 1  -> PURPLE;
                        default -> WHITE_YELLOW;
                    };
                    spawnParticles(world, col, cx + (world.random.nextDouble() - 0.5) * 0.20, h, cz + (world.random.nextDouble() - 0.5) * 0.20, 1, 0.02, 0.06, 0.02, 0.006);
                }
            }
        }

        if (t % 2 == 0) spawnFallingColumn(world, pos, (int)(6 + progress * 10), 1.0, 7.0, 14.0);

        if (t == 22) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.7f, 0.45f, false);
        if (t == 46) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.75f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 0.8f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.7f, 1.1f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        long time = world.getGameTime();
        double sigRotation = time * 0.055;
        double shrink = 1.0 - progress * 0.84;
        double sigR = 5.0 * shrink + 0.8;
        double sigY = pos.y + 1.2 - progress * 0.6;

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int steps = 8;
                for (int s = 0; s < steps; s++) {
                    double d = 0.2 + (double) s / steps * sigR;
                    DustParticleOptions col = (s % 2 == 0) ? PURPLE : MAGENTA;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * d, sigY, pos.z + Math.sin(angle) * d, 1, 0.03, 0.02, 0.03, 0.005);
                }
            }
        }

        double haloShrink = 1.0 - progress * 0.55;
        if (t % 2 == 0) {
            spawnHalo(world, pos, 2.4 * haloShrink, 1.6, time * (0.07 + progress * 0.08), 24, MAGENTA, VIOLET);
            spawnHalo(world, pos, 1.7 * haloShrink, 1.9, -time * (0.10 + progress * 0.08), 18, PURPLE, WHITE_YELLOW);
            spawnHalo(world, pos, 1.0 * haloShrink, 2.2, time * (0.14 + progress * 0.12), 12, WHITE_YELLOW, VIOLET);
        }

        if (t % 2 == 0) {
            int pullCount = (int)(10 + progress * 18);
            for (int i = 0; i < pullCount; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI;
                double srcR = 3.5 + world.random.nextDouble() * 5.0;
                double fromX = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY = pos.y + 1.0 + Math.cos(phi) * srcR * 0.45;
                double fromZ = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3 toward = pos.add(0, 1, 0).subtract(fromX, fromY, fromZ).normalize().scale(0.10 + progress * 0.07);

                DustParticleOptions col = switch (i % 4) {
                    case 0  -> MAGENTA;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    default -> WHITE_YELLOW;
                };
                spawnParticles(world, col, fromX, fromY, fromZ, 1, toward.x, toward.y, toward.z, 0.010);
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI * 0.5;
                double speed = 0.10 + progress * 0.05;
                DustParticleOptions col = (i % 2 == 0) ? MAGENTA : WHITE_YELLOW;
                spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed * 0.5 + 0.04, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }
        }

        if (t % 2 == 0) spawnFallingColumn(world, pos, (int)(10 + progress * 10), 0.7, 8.0, 15.0);
        if (t % 8 == 0) spawnParticles(world, ParticleTypes.ENCHANT, pos.x, pos.y + 1.0, pos.z, 10, 0.6, 0.7, 0.6, 1.8);

        if (t % 10 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, (float)(0.6 + progress * 0.4), 0.9f + (float) progress * 0.5f, false);
        if (t == 30) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.2f, false);
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3f, 0.65f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9f, 0.75f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.80f, false);

            for (int d = 0; d < 48; d++) {
                double theta = d * Math.PI * 2.0 / 48;
                double phi = world.random.nextDouble() * Math.PI;
                double speed = 0.22 + world.random.nextDouble() * 0.16;
                DustParticleOptions col = switch (d % 5) {
                    case 0  -> MAGENTA;
                    case 1  -> WHITE_YELLOW;
                    case 2  -> PURPLE;
                    case 3  -> VIOLET;
                    default -> WHITE;
                };
                spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }

            for (double ringScale : new double[]{ 0.5, 1.0, 1.6 }) {
                int ringPoints = 20;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = i * Math.PI * 2.0 / ringPoints;
                    spawnParticles(world, ringScale < 1.0 ? WHITE : PURPLE, pos.x + Math.cos(angle) * ringScale * 0.4, pos.y + 0.15, pos.z + Math.sin(angle) * ringScale * 0.4, 1, Math.cos(angle) * 0.32 * ringScale, 0.01, Math.sin(angle) * 0.32 * ringScale, 0.0);
                }
            }

            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.2, pos.z, 5, 0.4, 0.4, 0.4, 0);
            spawnParticles(world, ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z, 20, 0.8, 0.7, 0.8, 0);
            spawnParticles(world, ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 20, 1.0, 1.2, 1.0, 0.20);
        }

        double progress = (double) t / STAGE_4_TICKS;
        long time = world.getGameTime();

        if (t % 2 == 0 && progress < 0.70f) {
            spawnFallingColumn(world, pos, (int)(14 + (1.0 - progress) * 10), 0.6, 8.0, 16.0);
        }

        double finalShrink = 0.7 - progress * 0.25;
        if (t % 2 == 0 && progress < 0.80f) {
            spawnHalo(world, pos, Math.max(0.3, 2.4 * finalShrink), 1.6, time * 0.18, 24, MAGENTA, WHITE);
            spawnHalo(world, pos, Math.max(0.3, 1.7 * finalShrink), 1.9, -time * 0.22, 18, PURPLE, WHITE_YELLOW);
            spawnHalo(world, pos, Math.max(0.2, 1.0 * finalShrink), 2.2, time * 0.28, 12, WHITE, VIOLET);
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi = world.random.nextDouble() * Math.PI * 0.7;
                double speed = 0.08 + (1.0 - progress) * 0.06;
                spawnParticles(world, WHITE, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed * 0.6 + 0.03, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                spawnParticles(world, ParticleTypes.TOTEM_OF_UNDYING, pos.x + (world.random.nextDouble() - 0.5) * 0.7, pos.y + 0.3 + world.random.nextDouble() * 1.8, pos.z + (world.random.nextDouble() - 0.5) * 0.7, 1, (world.random.nextDouble() - 0.5) * 0.04, 0.10 + world.random.nextDouble() * 0.12, (world.random.nextDouble() - 0.5) * 0.04, 0.0);
            }
        }

        if (progress > 0.75f) {
            spawnParticles(world, WHITE, pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.004);
            spawnParticles(world, PINK_WHITE, pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.003);
        }

        if (t % 8 == 0) spawnParticles(world, ParticleTypes.ENCHANT, pos.x, pos.y + 1.0, pos.z, 10, 0.6, 0.8, 0.6, 2.0);

        if (t % 7 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6f + (float) progress * 0.4f, 0.65f + (float) progress * 1.0f, false);
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();

        for (int d = 0; d < 40; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi = world.random.nextDouble() * Math.PI;
            double speed = 0.18;
            DustParticleOptions col = switch (d % 4) {
                case 0  -> WHITE;
                case 1  -> MAGENTA;
                case 2  -> PURPLE;
                default -> WHITE_YELLOW;
            };
            spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
        }

        for (double angle : SIGIL_ANGLES) {
            for (double d = 0.5; d <= 4.0; d += 0.6) {
                spawnParticles(world, d < 2.0 ? WHITE : MAGENTA, pos.x + Math.cos(angle) * d, pos.y + 0.1, pos.z + Math.sin(angle) * d, 1, Math.cos(angle) * 0.12, 0.01, Math.sin(angle) * 0.12, 0.0);
            }
        }

        spawnParticles(world, WHITE, pos.x, pos.y + 1.0, pos.z, 22, 1.5, 1.2, 1.5, 0.10);
        spawnParticles(world, MAGENTA, pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 16, 1.1, 1.0, 1.1, 0.08);
        spawnParticles(world, WHITE_YELLOW, pos.x, pos.y + 1.0, pos.z, 14, 1.0, 0.9, 1.0, 0.07);
        spawnParticles(world, VIOLET, pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        spawnParticles(world, ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 24, 1.2, 1.5, 1.2, 0.22);
        spawnParticles(world, ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z, 16, 1.0, 0.8, 1.0, 0);
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.4, pos.z, 5, 0.3, 0.3, 0.3, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.5f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.4f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.6f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.6f, 2.0f, false);
    }
}