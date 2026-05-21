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

public class MotionRitualClient {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    // Particles
    private static final DustParticleOptions BLUE_DEEP = new DustParticleOptions(new Vector3f(0.05f, 0.25f, 0.85f), 1.4f);
    private static final DustParticleOptions BLUE_LIGHT = new DustParticleOptions(new Vector3f(0.15f, 0.60f, 1.00f), 1.3f);
    private static final DustParticleOptions CYAN_BRIGHT = new DustParticleOptions(new Vector3f(0.20f, 0.90f, 1.00f), 1.1f);
    private static final DustParticleOptions BLUE_PALE = new DustParticleOptions(new Vector3f(0.70f, 0.85f, 1.00f), 1.0f);
    private static final DustParticleOptions NAVY = new DustParticleOptions(new Vector3f(0.00f, 0.08f, 0.35f), 1.5f);

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
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.6f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        int pulseTimer = t % 12;
        double shockRadius = pulseTimer * 0.35;

        if (shockRadius > 0.3 && t % 2 == 0) {
            int shockPoints = 14;
            for (int i = 0; i < shockPoints; i++) {
                double angle = Math.PI * 2.0 * i / shockPoints;
                DustParticleOptions col = (pulseTimer < 6) ? BLUE_LIGHT : BLUE_DEEP;
                spawnParticles(world, col, pos.x + Math.cos(angle) * shockRadius, pos.y + 0.05, pos.z + Math.sin(angle) * shockRadius, 1, 0.04, 0.02, 0.04, 0.003);
            }
        }

        if (t % 5 == 0) {
            int rays = 8;
            double maxR = 1.5 + progress * 1.5;
            for (int r = 0; r < rays; r++) {
                double angle = r * Math.PI * 2.0 / rays;
                for (double d = 0.4; d <= maxR; d += 0.5) {
                    spawnParticles(world, BLUE_DEEP, pos.x + Math.cos(angle) * d, pos.y + 0.1, pos.z + Math.sin(angle) * d, 1, 0.01, 0.02, 0.01, 0.015);
                }
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double h = world.random.nextDouble() * (1.5 + progress * 1.5);
                spawnParticles(world, BLUE_LIGHT, pos.x + (world.random.nextDouble() - 0.5) * 0.4, pos.y + h, pos.z + (world.random.nextDouble() - 0.5) * 0.4, 1, 0.02, 0.04, 0.02, 0.010);
            }
        }

        if (t % 3 == 0) {
            spawnParticles(world, ParticleTypes.SWEEP_ATTACK, pos.x + (world.random.nextDouble() - 0.5) * 1.5, pos.y + 0.5 + world.random.nextDouble(), pos.z + (world.random.nextDouble() - 0.5) * 1.5, 1, 0, 0, 0, 1.0);
        }

        if (t % 20 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.3f, 0.5f + (float) progress * 0.4f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.7f, 0.5f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();

        double[][] rings = {
                { 2.5, 0.4,  0.08, 0.00, 0 },
                { 1.8, 1.0, -0.12, 0.55, 1 },
                { 2.1, 0.7,  0.06, 0.00, 0 },
                { 1.3, 1.5, -0.10, 0.70, 2 },
        };
        DustParticleOptions[] palette = { BLUE_DEEP, BLUE_LIGHT, CYAN_BRIGHT, BLUE_PALE };
        int arcPoints = (int)(8 + progress * 10);

        for (int ri = 0; ri < rings.length; ri++) {
            double[] ring = rings[ri];
            double r = ring[0], baseY = pos.y + ring[1], speed = ring[2], wobble = ring[3], wobFreq = ring[4];
            DustParticleOptions col = palette[ri];

            for (int i = 0; i < arcPoints; i++) {
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double y = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);

                if (t % 2 == 0 || i < 4) {
                    spawnParticles(world, col, pos.x + Math.cos(theta) * r, y, pos.z + Math.sin(theta) * r, 1, 0.03, 0.02, 0.03, 0.005);
                }

                if (i == arcPoints - 1 && t % 3 == 0) {
                    spawnParticles(world, NAVY, pos.x + Math.cos(theta - 0.15) * r, y, pos.z + Math.sin(theta - 0.15) * r, 1, 0.04, 0.02, 0.04, 0.003);
                }
            }
        }

        if (t % 8 == 0) {
            int rays = 6;
            double gyroTime = time * 0.03;
            for (int r = 0; r < rays; r++) {
                double angle = r * Math.PI * 2.0 / rays + gyroTime;
                spawnParticles(world, BLUE_LIGHT, pos.x + Math.cos(angle) * 1.2, pos.y + 0.5, pos.z + Math.sin(angle) * 1.2, 1, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05, 0.02);
            }
        }

        if (t == 20) world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.GUST.get(), SoundSource.PLAYERS, 0.5f, 1.4f, false);
        if (t == 40) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.2f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.5f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        long time = world.getGameTime();
        double shrink = 1.0 - progress * 0.65;
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
            double[] ring = rings[ri];
            double r = ring[0], baseY = pos.y + ring[1], speed = ring[2], wobble = ring[3], wobFreq = ring[4];
            DustParticleOptions col = palette[ri];

            for (int i = 0; i < arcPoints; i++) {
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double y = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);
                if (t % 2 == 0) {
                    spawnParticles(world, col, pos.x + Math.cos(theta) * r, y, pos.z + Math.sin(theta) * r, 1, 0.04, 0.03, 0.04, 0.008);
                }
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double srcR = 3.5 + world.random.nextDouble() * 1.5;
                double srcY = pos.y + world.random.nextDouble() * 2.0;
                Vec3 from = new Vec3(pos.x + Math.cos(angle) * srcR, srcY, pos.z + Math.sin(angle) * srcR);
                Vec3 toward = pos.add(0, 1, 0).subtract(from).normalize().scale(0.10 + progress * 0.06);
                DustParticleOptions col = world.random.nextBoolean() ? BLUE_LIGHT : CYAN_BRIGHT;
                // Count = 1 in original, translating dx/dy/dz to standard deviation.
                spawnParticles(world, col, from.x, from.y, from.z, 1, toward.x, toward.y, toward.z, 0.014);
            }
        }

        if (t % 10 == 0) {
            for (double shockY : new double[]{ 0.5, 1.3 }) {
                int shockPoints = 16;
                for (int i = 0; i < shockPoints; i++) {
                    double angle = Math.PI * 2.0 * i / shockPoints;
                    double vel = 0.06 + progress * 0.04;
                    spawnParticles(world, CYAN_BRIGHT, pos.x + Math.cos(angle) * 0.3, pos.y + shockY, pos.z + Math.sin(angle) * 0.3, 1, Math.cos(angle) * vel, 0.005, Math.sin(angle) * vel, 0.0);
                }
            }
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, (float)(0.4 + progress * 0.3), 1.3f + (float) progress * 0.4f, false);
        }
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.6f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.3f, false);

            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI * 2.0 / 8;
                for (double r = 0.5; r <= 3.0; r += 0.5) {
                    spawnParticles(world, BLUE_LIGHT, pos.x + Math.cos(angle) * r, pos.y + 0.5, pos.z + Math.sin(angle) * r, 1, Math.cos(angle) * 0.08, 0.02, Math.sin(angle) * 0.08, 0.0);
                }
            }
            spawnParticles(world, CYAN_BRIGHT, pos.x, pos.y + 1.0, pos.z, 16, 1.5, 1.2, 1.5, 0.12);
            spawnParticles(world, ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z, 4, 0.8, 0.5, 0.8, 0.0);
        }

        double progress = (double) t / STAGE_4_TICKS;
        int rayCount = (int)(12 + (1.0 - progress) * 12);

        if (t % 2 == 0) {
            for (int r = 0; r < rayCount; r++) {
                double angle = r * Math.PI * 2.0 / rayCount + (t * 0.07);
                double vel = 0.08 + (1.0 - progress) * 0.05;
                double h = 0.3 + world.random.nextDouble() * 2.0;
                DustParticleOptions col = switch (r % 3) {
                    case 0  -> BLUE_DEEP;
                    case 1  -> BLUE_LIGHT;
                    default -> CYAN_BRIGHT;
                };
                spawnParticles(world, col, pos.x + Math.cos(angle) * 0.5, pos.y + h, pos.z + Math.sin(angle) * 0.5, 1, Math.cos(angle) * vel, 0.01, Math.sin(angle) * vel, 0.0);
            }
        }

        if (t % 6 == 0) {
            spawnParticles(world, ParticleTypes.SWEEP_ATTACK, pos.x + (world.random.nextDouble() - 0.5) * 1.5, pos.y + 0.5 + world.random.nextDouble() * 1.5, pos.z + (world.random.nextDouble() - 0.5) * 1.5, 1, 0, 0, 0, 1.0);
        }

        if (progress > 0.80f) {
            for (int i = 0; i < 3; i++) {
                spawnParticles(world, BLUE_PALE, pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.004);
                spawnParticles(world, NAVY, pos.x, pos.y + 0.5, pos.z, 1, 0.4, 0.2, 0.4, 0.003);
            }
        }

        if (t % 5 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f + (float) progress * 0.3f, 1.5f + (float) progress * 0.5f, false);
        if (t % 8 == 0) world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSLOOP.get(), SoundSource.PLAYERS, 0.6f, 1.6f, false);
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI * 2.0 / 8;
            spawnParticles(world, BLUE_LIGHT, pos.x + Math.cos(angle) * 0.5, pos.y + 1.0, pos.z + Math.sin(angle) * 0.5, 4, Math.cos(angle) * 0.10, 0.05, Math.sin(angle) * 0.10, 0.0);
        }

        spawnParticles(world, CYAN_BRIGHT, pos.x, pos.y + 1.0, pos.z, 12, 1.2, 1.0, 1.2, 0.09);
        spawnParticles(world, BLUE_PALE, pos.x, pos.y + 1.0, pos.z, 8, 0.8, 0.7, 0.8, 0.05);
        spawnParticles(world, ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z, 3, 0.5, 0.3, 0.5, 0.0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.9f, false);
    }
}