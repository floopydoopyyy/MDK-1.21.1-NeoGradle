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

public class MindRitualClient {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    // Particles
    private static final DustParticleOptions PINK_HOT = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.58f), 1.4f);
    private static final DustParticleOptions PINK_PALE = new DustParticleOptions(new Vector3f(1.0f, 0.60f, 0.85f), 1.1f);
    private static final DustParticleOptions PURPLE_DEEP = new DustParticleOptions(new Vector3f(0.45f, 0.0f, 0.70f), 1.5f);
    private static final DustParticleOptions PURPLE_SOFT = new DustParticleOptions(new Vector3f(0.70f, 0.30f, 1.0f), 1.2f);
    private static final DustParticleOptions PURPLE_BLACK = new DustParticleOptions(new Vector3f(0.15f, 0.0f, 0.25f), 1.6f);

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

    private static void tickStage1(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.6f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();

        int ringCount = 2 + (int)(progress * 2);
        for (int ring = 0; ring < ringCount; ring++) {
            double phase = (time * 0.04 + ring * 0.5) % (Math.PI * 2);
            double r = 1.0 + ring * 0.9 + Math.sin(phase) * 0.2;
            int points = 10 + ring * 4;
            if (t % 3 == 0) {
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    DustParticleOptions col = (ring % 2 == 0) ? PINK_HOT : PURPLE_DEEP;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + 0.03, pos.z + Math.sin(angle) * r, 1, 0.04, 0.02, 0.04, 0.003);
                }
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.5;
                double h = 0.5 + world.random.nextDouble() * 3.5 * progress;
                DustParticleOptions wisp = world.random.nextBoolean() ? PINK_PALE : PURPLE_SOFT;
                spawnParticles(world, wisp, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0.02, 0.05, 0.02, 0.008);
            }
        }

        if (t % 8 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = 0.5 + world.random.nextDouble() * 0.8;
            spawnParticles(world, PURPLE_BLACK, pos.x + Math.cos(angle) * r, pos.y + 0.5, pos.z + Math.sin(angle) * r, 1, 0.01, 0.02, 0.01, 0.005);
        }

        if (t % 25 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.3f, 0.5f + (float) progress * 0.3f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.8f, 0.7f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();
        double maxHeight = 10.0 * progress;
        double orbitRadius = 2.2;

        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);
            int steps = (int)(maxHeight / 0.4) + 1;

            for (int s = 0; s < steps; s++) {
                double y = pos.y + s * 0.4;
                double outerAngle = baseAngle + s * 0.35 + time * 0.05;

                if (t % 2 == 0) {
                    DustParticleOptions col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    spawnParticles(world, col, pos.x + Math.cos(outerAngle) * orbitRadius, y, pos.z + Math.sin(outerAngle) * orbitRadius, 1, 0.05, 0.03, 0.05, 0.007);
                }

                if (t % 3 == 0 && s % 2 == 0) {
                    double innerAngle = baseAngle - s * 0.30 - time * 0.04;
                    spawnParticles(world, PURPLE_SOFT, pos.x + Math.cos(innerAngle) * (orbitRadius * 0.55), y + 0.1, pos.z + Math.sin(innerAngle) * (orbitRadius * 0.55), 1, 0.03, 0.02, 0.03, 0.005);
                }
            }

            if (maxHeight > 1.5 && t % 4 == 0) {
                spawnParticles(world, PINK_HOT, pos.x, pos.y + maxHeight, pos.z, 2, 0.5, 0.15, 0.5, 0.02);
            }
        }

        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 0.4 + world.random.nextDouble() * 2.5;
                spawnParticles(world, PURPLE_BLACK, pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r, 1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        if (t == 20) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.3f, false);
        if (t == 40) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.5f, 1.6f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.4f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        long time = world.getGameTime();
        double orbitRadius = 2.2 - progress * 1.7;

        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);
            int steps = (int)(10.0 * (0.5 + progress * 0.5));

            for (int s = 0; s < steps; s++) {
                if (t % 2 == 0) {
                    double spin = baseAngle + s * (0.35 + progress * 0.25) + time * (0.06 + progress * 0.06);
                    double y = pos.y + s * (0.4 - progress * 0.1);
                    DustParticleOptions col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    spawnParticles(world, col, pos.x + Math.cos(spin) * orbitRadius, y, pos.z + Math.sin(spin) * orbitRadius, 1, 0.04 + progress * 0.04, 0.03, 0.04 + progress * 0.04, 0.01);
                }
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = orbitRadius + 1.5;
                Vec3 from = new Vec3(pos.x + Math.cos(angle) * r, pos.y + 1.0, pos.z + Math.sin(angle) * r);
                Vec3 toward = pos.add(0, 1, 0).subtract(from).normalize().scale(0.07 + progress * 0.05);
                DustParticleOptions col = world.random.nextBoolean() ? PINK_HOT : PURPLE_BLACK;
                // Count = 1 on server with directional dx/dy/dz acts as jittered movement.
                spawnParticles(world, col, from.x, from.y, from.z, 1, toward.x, toward.y, toward.z, 0.012);
            }
        }

        if (t % 3 == 0) {
            double headRing = 0.8 - progress * 0.2;
            for (int i = 0; i < 8; i++) {
                double angle = time * 0.15 + i * Math.PI * 2.0 / 8;
                spawnParticles(world, PURPLE_SOFT, pos.x + Math.cos(angle) * headRing, pos.y + 1.6, pos.z + Math.sin(angle) * headRing, 1, 0.02, 0.02, 0.02, 0.008);
            }
        }

        if (t % 15 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS, (float)(0.3 + progress * 0.4), 1.2f + (float) progress * 0.3f, false);
        }
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.4f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.8f, 0.8f, false);

            spawnParticles(world, PINK_HOT, pos.x, pos.y + 1.0, pos.z, 20, 1.5, 1.2, 1.5, 0.12);
            spawnParticles(world, PURPLE_DEEP, pos.x, pos.y + 1.0, pos.z, 15, 1.2, 1.0, 1.2, 0.10);
            spawnParticles(world, PURPLE_BLACK, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.08);
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.3, 0.3, 0.3, 0);
        }

        double progress = (double) t / STAGE_4_TICKS;
        int burstCount = (int)(10 + (1.0 - progress) * 16);

        for (int i = 0; i < burstCount; i++) {
            DustParticleOptions col = switch (i % 3) {
                case 0  -> PINK_HOT;
                case 1  -> PURPLE_DEEP;
                default -> PURPLE_BLACK;
            };
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 2.0;
            double h = world.random.nextDouble() * 2.8;
            spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0, 0.02, 0, 0.018);
        }

        if (t % 3 == 0) spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 1.8, pos.y + world.random.nextDouble() * 2.5, pos.z + (world.random.nextDouble() - 0.5) * 1.8, 1, 0.04, 0.08, 0.04, 0.025);
        if (t % 4 == 0) spawnParticles(world, ParticleTypes.WITCH, pos.x + (world.random.nextDouble() - 0.5) * 1.2, pos.y + 0.5 + world.random.nextDouble() * 2.0, pos.z + (world.random.nextDouble() - 0.5) * 1.2, 2, 0.10, 0.10, 0.10, 0.02);

        if (progress > 0.78f) {
            for (int i = 0; i < 3; i++) {
                spawnParticles(world, PINK_PALE, pos.x, pos.y + 1.2, pos.z, 1, 0.5, 0.4, 0.5, 0.005);
                spawnParticles(world, PURPLE_SOFT, pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.004);
            }
        }

        if (t % 5 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.25f + (float) progress * 0.15f, 1.8f, false);
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        spawnParticles(world, PINK_HOT, pos.x, pos.y + 1.0, pos.z, 14, 1.2, 1.0, 1.2, 0.09);
        spawnParticles(world, PURPLE_DEEP, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.07);
        spawnParticles(world, PINK_PALE, pos.x, pos.y + 1.0, pos.z, 8, 0.8, 0.7, 0.8, 0.05);
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.1, 0.2, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.2f, false);
    }
}