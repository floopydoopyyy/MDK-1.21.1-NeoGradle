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

public class RuinRitualClient {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    // Particles
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.8f), 1.5f);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9f, 0.0f, 0.1f), 1.4f);
    private static final DustParticleOptions PURPLE_DARK = new DustParticleOptions(new Vector3f(0.2f, 0.0f, 0.3f), 1.6f);
    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.4f, 0.0f), 1.2f);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.05f), 1.3f);

    public static void tick(Level world, Player player, int ticks) {
        if (ticks == 1 || ticks % 28 == 0) {
            world.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    ModSounds.RITUALLOOP.get(), SoundSource.PLAYERS, 0.5f, 0.6f, false);
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
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 0.8f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.8f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();
        double r = 1.0 + progress * 2.5;

        if (t % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double angle = (time * 0.05) + (i * Math.PI * 2.0 / 16);
                DustParticleOptions col = (i % 2 == 0) ? PURPLE : PURPLE_DARK;
                spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r, 1, 0.03, 0.01, 0.03, 0.005);
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double rad = world.random.nextDouble() * r;
                DustParticleOptions col = world.random.nextBoolean() ? RED : ORANGE;
                spawnParticles(world, col, pos.x + Math.cos(angle) * rad, pos.y + 0.1, pos.z + Math.sin(angle) * rad, 1, 0.02, 0.06, 0.02, 0.01);
            }
        }

        if (t % 6 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            spawnParticles(world, ParticleTypes.LARGE_SMOKE, pos.x + Math.cos(angle) * r * 0.8, pos.y + 0.2, pos.z + Math.sin(angle) * r * 0.8, 1, 0.0, 0.05, 0.0, 0.01);
        }

        if (t % 20 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.5f, 0.7f + (float) progress * 0.4f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.8f, 0.6f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 0.7f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();
        double hMax = 4.0 + progress * 5.0;

        for (int c = 0; c < 4; c++) {
            double baseAngle = c * (Math.PI / 2.0);
            int steps = (int)(hMax / 0.5) + 1;
            for (int s = 0; s < steps; s++) {
                double y = pos.y + s * 0.5;
                double angle = baseAngle + s * 0.4 + time * 0.08;
                double r = 2.0 + Math.sin(time * 0.1 + s) * 0.5;

                if (t % 2 == 0) {
                    DustParticleOptions col = (s % 2 == 0) ? PURPLE_DARK : RED;
                    spawnParticles(world, col, pos.x + Math.cos(angle) * r, y, pos.z + Math.sin(angle) * r, 1, 0.05, 0.02, 0.05, 0.01);
                }
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 1.0 + world.random.nextDouble() * 3.0;
                spawnParticles(world, BLACK, pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r, 1, 0.03, 0.08, 0.03, 0.02);
            }
        }

        if (t % 5 == 0) {
            spawnParticles(world, ParticleTypes.FLAME, pos.x + (world.random.nextDouble() - 0.5) * 4.0, pos.y + world.random.nextDouble() * 2.0, pos.z + (world.random.nextDouble() - 0.5) * 4.0, 2, 0.05, 0.05, 0.05, 0.02);
        }

        if (t == 30) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.4f, 1.2f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 0.7f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.5f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        double r = 4.0 - progress * 2.5;

        for (int i = 0; i < 12; i++) {
            double u = world.random.nextDouble();
            double v = world.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double cx = pos.x + r * Math.sin(phi) * Math.cos(theta);
            double cy = pos.y + 1.0 + r * Math.cos(phi);
            double cz = pos.z + r * Math.sin(phi) * Math.sin(theta);

            DustParticleOptions col = switch (world.random.nextInt(4)) {
                case 0 -> PURPLE;
                case 1 -> RED;
                case 2 -> ORANGE;
                default -> BLACK;
            };

            spawnParticles(world, col, cx, cy, cz, 1, 0.02, 0.02, 0.02, 0.01);
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double dist = r + 1.5;
                Vec3 from = new Vec3(pos.x + Math.cos(angle) * dist, pos.y + 1.0 + world.random.nextDouble() * 2.0, pos.z + Math.sin(angle) * dist);
                Vec3 toward = pos.add(0, 1.0, 0).subtract(from).normalize().scale(0.1 + progress * 0.08);

                DustParticleOptions col = world.random.nextBoolean() ? PURPLE_DARK : RED;
                // Count = 1 with directional dx/dy/dz acts as momentum when using addParticle in spawnParticles
                spawnParticles(world, col, from.x, from.y, from.z, 1, toward.x, toward.y, toward.z, 0.02);
            }
        }

        if (t % 4 == 0) {
            spawnParticles(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x + (world.random.nextDouble() - 0.5) * 2.0, pos.y + 0.2 + world.random.nextDouble(), pos.z + (world.random.nextDouble() - 0.5) * 2.0, 1, 0, 0.04, 0, 0.02);
        }

        if (t % 12 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 0.5f + (float) progress * 0.5f, false);
        }
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3f, 0.5f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.6f, false);

            spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 25, 1.5, 1.2, 1.5, 0.15);
            spawnParticles(world, RED, pos.x, pos.y + 1.0, pos.z, 20, 1.3, 1.0, 1.3, 0.12);
            spawnParticles(world, BLACK, pos.x, pos.y + 1.0, pos.z, 15, 1.0, 0.8, 1.0, 0.10);
            spawnParticles(world, ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 3, 0.5, 0.5, 0.5, 0);
        }

        double progress = (double) t / STAGE_4_TICKS;
        int burstCount = (int)(12 + (1.0 - progress) * 15);

        for (int i = 0; i < burstCount; i++) {
            DustParticleOptions col = switch (world.random.nextInt(4)) {
                case 0  -> PURPLE;
                case 1  -> RED;
                case 2  -> PURPLE_DARK;
                default -> ORANGE;
            };
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 2.5;
            double h = world.random.nextDouble() * 3.0;
            spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0, 0.04, 0, 0.02);
        }

        if (t % 3 == 0) {
            spawnParticles(world, ParticleTypes.FLAME, pos.x + (world.random.nextDouble() - 0.5) * 3.0, pos.y + world.random.nextDouble() * 2.5, pos.z + (world.random.nextDouble() - 0.5) * 3.0, 2, 0.02, 0.06, 0.02, 0.015);
        }
        if (t % 4 == 0) {
            spawnParticles(world, ParticleTypes.LARGE_SMOKE, pos.x + (world.random.nextDouble() - 0.5) * 2.0, pos.y + 0.5 + world.random.nextDouble() * 2.0, pos.z + (world.random.nextDouble() - 0.5) * 2.0, 1, 0.05, 0.05, 0.05, 0.02);
        }

        if (t % 6 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSLOOP.get(), SoundSource.PLAYERS, 0.4f, 0.6f, false);
        }
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 14, 1.2, 1.0, 1.2, 0.09);
        spawnParticles(world, RED, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.08);
        spawnParticles(world, PURPLE_DARK, pos.x, pos.y + 1.0, pos.z, 8, 0.8, 0.7, 0.8, 0.06);
        spawnParticles(world, ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 2, 0.3, 0.2, 0.3, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.8f, false);
    }
}