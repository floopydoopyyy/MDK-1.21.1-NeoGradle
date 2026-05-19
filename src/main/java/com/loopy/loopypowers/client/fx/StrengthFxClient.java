package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.network.payload.StrengthParticlePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class StrengthFxClient {

    private static final DustParticleOptions RAGE_RED_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.35f);
    private static final DustParticleOptions RAGE_DARK_DUST = new DustParticleOptions(new Vector3f(0.15f, 0.0f, 0.0f), 1.6f);

    public static void onParticleEvent(StrengthParticlePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        RandomSource random = level.random;
        double x = payload.x(), y = payload.y(), z = payload.z();
        double dx = payload.dx(), dy = payload.dy(), dz = payload.dz();

        switch (payload.eventId()) {
            case StrengthParticlePayload.ONE_PUNCH -> {
                spawnParticles(level, ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 2, 0, 0, 0, 0);
                spawnParticles(level, ParticleTypes.FLASH, x, y + 1.0, z, 5, 1.0, 1.0, 1.0, 0);

                for (int i = 0; i < 35; i++) {
                    double step = i * 3.5;
                    double px = x + dx * step;
                    double py = y + 1.0 + dy * step;
                    double pz = z + dz * step;

                    spawnParticles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 5, 0.5, 0.5, 0.5, 0.1);
                    spawnParticles(level, ParticleTypes.CLOUD, px, py, pz, 10, 3.0, 3.0, 3.0, 0.3);

                    if (i % 3 == 0) {
                        spawnParticles(level, ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 1, 0, 0, 0, 0);
                        spawnParticles(level, ParticleTypes.EXPLOSION, px, py, pz, 2, 4.0, 4.0, 4.0, 0);
                    }
                }
            }
            case StrengthParticlePayload.RAGE_HIT -> {
                spawnParticles(level, ParticleTypes.CLOUD, x, y, z, 14, 0.18, 0.18, 0.18, 0.02);
            }
            case StrengthParticlePayload.GROUND_SLAM -> {
                boolean casterGrounded = payload.flag();
                BlockState groundState = Block.stateById(payload.stateId());

                spawnParticles(level, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);

                if (casterGrounded) {
                    spawnParticles(level, ParticleTypes.EXPLOSION, x, y + 0.05, z, 6, 0.35, 0.15, 0.35, 0.02);
                    spawnParticles(level, ParticleTypes.POOF, x, y, z, 12, 0.55, 0.15, 0.55, 0.03);
                }

                if (!groundState.isAir()) {
                    int dustCountA = casterGrounded ? 420 : 20;
                    int dustCountB = casterGrounded ? 220 : 8;
                    BlockParticleOption blockDust = new BlockParticleOption(ParticleTypes.BLOCK, groundState);

                    spawnParticles(level, blockDust, x, y + 0.91, z, dustCountA, casterGrounded ? 2.2 : 0.5, casterGrounded ? 0.18 : 0.08, casterGrounded ? 2.2 : 0.5, casterGrounded ? 0.75 : 0.08);
                    spawnParticles(level, blockDust, x, y + 0.91, z, dustCountB, casterGrounded ? 0.65 : 0.20, casterGrounded ? 1.10 : 0.25, casterGrounded ? 0.65 : 0.20, casterGrounded ? 1.15 : 0.12);

                    if (casterGrounded) {
                        double[] radii = {1.4, 2.8, 4.2};
                        int[] counts = {24, 32, 42};
                        double[] spreads = {0.25, 0.30, 0.38};
                        double[] speeds = {0.35, 0.40, 0.45};

                        for (int ri = 0; ri < radii.length; ri++) {
                            double r = radii[ri];
                            int n = counts[ri];
                            for (int i = 0; i < n; i++) {
                                double a = random.nextDouble() * (Math.PI * 2.0);
                                double jr = (random.nextDouble() - 0.5) * 0.35;
                                double px = x + Math.cos(a) * (r + jr);
                                double pz = z + Math.sin(a) * (r + jr);
                                spawnParticles(level, blockDust, px, y + 0.91, pz, 1, spreads[ri], 0.08, spreads[ri], speeds[ri]);
                            }
                        }
                        spawnParticles(level, ParticleTypes.CLOUD, x, y + 0.95, z, 140, 1.1, 0.25, 1.1, 0.10);
                        spawnParticles(level, ParticleTypes.CRIT, x, y + 0.95, z, 55, 0.7, 0.20, 0.7, 0.12);
                    }
                }
            }
            case StrengthParticlePayload.SLAM_TARGET_CRIT -> {
                spawnParticles(level, ParticleTypes.CRIT, x, y, z, 2, 0.12, 0.12, 0.12, 0.0);
            }
            case StrengthParticlePayload.RUSH_CANCEL -> {
                spawnParticles(level, ParticleTypes.CLOUD, x, y, z, 10, 0.25, 0.10, 0.25, 0.02);
            }
            case StrengthParticlePayload.RUSH_HIT -> {
                spawnParticles(level, ParticleTypes.CRIT, x, y, z, 8, 0.16, 0.16, 0.16, 0.02);
                spawnParticles(level, ParticleTypes.CLOUD, x, y - 0.4, z, 14, 0.25, 0.10, 0.25, 0.04);
            }
            case StrengthParticlePayload.RUSH_CRASH -> {
                spawnParticles(level, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
                spawnParticles(level, ParticleTypes.CLOUD, x, y, z, 120, 1.0, 0.35, 1.0, 0.12);
                spawnParticles(level, ParticleTypes.CRIT, x, y, z, 50, 0.8, 0.25, 0.8, 0.18);
            }
            case StrengthParticlePayload.RAGE_PULSE -> {
                spawnParticles(level, RAGE_RED_DUST, x, y, z, 28, 0.12, 0.18, 0.12, 0.06);

                int points = 24;
                double radius = 3.35;
                double speed = 0.45;
                for (int i = 0; i < points; i++) {
                    double a = (Math.PI * 2.0) * (i / (double) points);
                    double pdx = Math.cos(a);
                    double pdz = Math.sin(a);
                    spawnParticles(level, RAGE_RED_DUST, x + pdx * radius, y - 0.85, z + pdz * radius, 10, pdx * speed, 0.03, pdz * speed, 1.0);
                }
                spawnParticles(level, ParticleTypes.EXPLOSION_EMITTER, x, y - 0.65, z, 1, 0, 0, 0, 0);
                spawnParticles(level, ParticleTypes.POOF, x, y - 0.9, z, 10, 0.35, 0.05, 0.35, 0.05);
            }
            case StrengthParticlePayload.RUSH_TICK -> {
                boolean playStep = payload.flag();
                BlockState groundState = Block.stateById(payload.stateId());

                Vector3f horiz = new Vector3f((float)dx, 0, (float)dz).normalize();
                Vector3f right = new Vector3f(-horiz.z(), 0, horiz.x()).normalize();
                Vector3f left = new Vector3f(horiz.z(), 0, -horiz.x()).normalize();

                if (playStep && !groundState.isAir()) {
                    BlockParticleOption stepDust = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
                    spawnParticles(level, stepDust, x, y + 0.1, z, 4, 0.3, 0.1, 0.3, 0.1);
                }

                spawnParticles(level, ParticleTypes.CLOUD, x + right.x() * 0.6, y + 1.0, z + right.z() * 0.6, 1, 0.0, 0.0, 0.0, 0.05);
                spawnParticles(level, ParticleTypes.CLOUD, x + left.x() * 0.6, y + 1.0, z + left.z() * 0.6, 1, 0.0, 0.0, 0.0, 0.05);
                spawnParticles(level, ParticleTypes.CRIT, x + horiz.x() * 0.8, y + 0.8, z + horiz.z() * 0.8, 3, 0.2, 0.4, 0.2, 0.0);
            }
            case StrengthParticlePayload.RAGE_TICK -> {
                spawnParticles(level, RAGE_RED_DUST, x, y + 1.0, z, 3, 0.65, 0.85, 0.65, 0.02);
                spawnParticles(level, RAGE_DARK_DUST, x, y + 0.2, z, 4, 0.8, 0.15, 0.8, 0.01);

                if (random.nextFloat() < 0.4f) {
                    spawnParticles(level, ParticleTypes.WHITE_ASH, x, y + 0.5, z, 3, 0.6, 0.5, 0.6, 0.05);
                    spawnParticles(level, ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, 1, 0.5, 0.2, 0.5, 0.03);
                }

                if (random.nextFloat() < 0.25f) {
                    spawnParticles(level, ParticleTypes.FLAME, x, y + 0.1, z, 2, 0.55, 0.1, 0.55, 0.04);
                }
            }
        }
    }

    private static void spawnParticles(ClientLevel level, ParticleOptions type, double x, double y, double z, int count, double xDist, double yDist, double zDist, double maxSpeed) {
        RandomSource random = level.random;
        if (count == 0) {
            level.addParticle(type, x, y, z, xDist, yDist, zDist);
            return;
        }
        for (int i = 0; i < count; i++) {
            double px = x + random.nextGaussian() * xDist;
            double py = y + random.nextGaussian() * yDist;
            double pz = z + random.nextGaussian() * zDist;
            double vx = random.nextGaussian() * maxSpeed;
            double vy = random.nextGaussian() * maxSpeed;
            double vz = random.nextGaussian() * maxSpeed;
            level.addParticle(type, px, py, pz, vx, vy, vz);
        }
    }
}