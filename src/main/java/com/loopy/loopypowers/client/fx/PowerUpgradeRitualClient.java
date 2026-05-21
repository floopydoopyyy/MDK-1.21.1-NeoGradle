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

public class PowerUpgradeRitualClient {

    private static final int STAGE_1_TICKS = 50;
    private static final int STAGE_2_TICKS = 45;
    private static final int STAGE_3_TICKS = 55;

    // Particles
    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(0.95f, 0.15f, 0.70f), 1.4f);
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.52f, 0.08f, 0.80f), 1.3f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.00f, 0.40f, 0.80f), 1.2f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.75f, 0.35f, 0.95f), 1.1f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.00f, 0.95f, 1.00f), 1.5f);

    public static void tick(Level world, Player player, int ticks) {
        int stage = currentStage(ticks);
        int stageTick = stageLocalTick(ticks, stage);

        switch (stage) {
            case 1 -> tickStage1(world, player, stageTick);
            case 2 -> tickStage2(world, player, stageTick);
            case 3 -> tickStage3(world, player, stageTick);
        }

        if (ticks >= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) {
            onComplete(world, player);
        }
    }

    private static int currentStage(int ticks) {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        return 3;
    }

    private static int stageLocalTick(int ticks, int stage) {
        return switch (stage) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
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
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.6f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 0.4f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();

        int spiralArms = 4;
        for (int i = 0; i < spiralArms; i++) {
            double angle = (time * 0.08) + (Math.PI * 2.0 * i / spiralArms);
            double r = 1.0 + progress * 2.0;
            DustParticleOptions col = (i % 2 == 0) ? MAGENTA : PURPLE;
            spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r, 1, 0.02, 0.01, 0.02, 0.005);
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 2.5;
                spawnParticles(world, VIOLET, pos.x + Math.cos(angle) * r, pos.y + 0.1 + world.random.nextDouble() * 2.0 * progress, pos.z + Math.sin(angle) * r, 1, 0.01, 0.03, 0.01, 0.008);
            }
        }

        if (t % 15 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5f, 0.5f + (float) progress * 0.5f, false);
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 0.8f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();

        double ringR = 2.0 - progress * 0.5;
        if (t % 2 == 0) {
            int ringPoints = 16;
            for (int i = 0; i < ringPoints; i++) {
                double angle1 = (time * 0.1) + i * Math.PI * 2.0 / ringPoints;
                spawnParticles(world, MAGENTA, pos.x + Math.cos(angle1) * ringR, pos.y + 0.8 + Math.sin(time * 0.05 + i) * 0.4, pos.z + Math.sin(angle1) * ringR, 1, 0.01, 0.01, 0.01, 0.004);

                double angle2 = -(time * 0.12) + i * Math.PI * 2.0 / ringPoints;
                spawnParticles(world, PURPLE, pos.x + Math.cos(angle2) * (ringR * 0.7), pos.y + 1.2 + Math.cos(time * 0.06 + i) * 0.3, pos.z + Math.sin(angle2) * (ringR * 0.7), 1, 0.01, 0.01, 0.01, 0.004);
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 2.0;
                spawnParticles(world, WHITE, pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r, 1, 0, 0.05, 0, 0.015);
            }
        }

        if (t == 20) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.7f, 0.6f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.8f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 0.9f, false);

            for (int d = 0; d < 32; d++) {
                double theta = d * Math.PI * 2.0 / 32;
                double phi = world.random.nextDouble() * Math.PI;
                double speed = 0.15 + world.random.nextDouble() * 0.15;
                DustParticleOptions col = (d % 2 == 0) ? MAGENTA : WHITE;
                spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 1, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed, 0.0);
            }
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.2, 0.2, 0.2, 0);
        }

        double progress = (double) t / STAGE_3_TICKS;

        double sphereRadius = 1.8 * (1.0 - progress * 0.6);
        if (t % 2 == 0) {
            int points = 16;
            for (int i = 0; i < points; i++) {
                double u = world.random.nextDouble();
                double v = world.random.nextDouble();
                double theta = 2.0 * Math.PI * u;
                double phi = Math.acos(2.0 * v - 1.0);

                double px = pos.x + Math.sin(phi) * Math.cos(theta) * sphereRadius;
                double py = pos.y + 1.0 + Math.cos(phi) * sphereRadius;
                double pz = pos.z + Math.sin(phi) * Math.sin(theta) * sphereRadius;

                DustParticleOptions col = switch (world.random.nextInt(3)) {
                    case 0  -> WHITE;
                    case 1  -> PINK;
                    default -> VIOLET;
                };
                spawnParticles(world, col, px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = 2.0 + world.random.nextDouble() * 3.0;
                Vec3 from = new Vec3(pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r);
                Vec3 dir = pos.add(0, 1.0, 0).subtract(from).normalize().scale(0.12);
                spawnParticles(world, PURPLE, from.x, from.y, from.z, 1, dir.x, dir.y, dir.z, 0.015);
            }
        }

        if (t % 6 == 0) spawnParticles(world, ParticleTypes.ENCHANT, pos.x, pos.y + 1.0, pos.z, 8, 0.5, 0.6, 0.5, 1.5);

        if (t % 10 == 0) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.8f + (float) progress * 0.8f, false);
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();

        int ringPoints = 36;
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.PI * 2.0 * i / ringPoints;
            spawnParticles(world, WHITE, pos.x + Math.cos(angle) * 1.5, pos.y + 0.1, pos.z + Math.sin(angle) * 1.5, 2, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05, 0.0);
        }

        spawnParticles(world, MAGENTA, pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        spawnParticles(world, PURPLE, pos.x, pos.y + 1.0, pos.z, 14, 1.1, 1.0, 1.1, 0.08);
        spawnParticles(world, WHITE, pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        spawnParticles(world, VIOLET, pos.x, pos.y + 1.0, pos.z, 10, 0.8, 0.8, 0.8, 0.07);
        spawnParticles(world, PINK, pos.x, pos.y + 1.0, pos.z, 8, 0.7, 0.7, 0.7, 0.06);
        spawnParticles(world, ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 16, 1.0, 1.2, 1.0, 0.18);
        spawnParticles(world, ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z, 10, 0.8, 0.7, 0.8, 0);
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.2, pos.z, 3, 0.2, 0.2, 0.2, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.2f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.1f, false);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.4f, false);
    }
}