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

public class PowerRitualClient {

    private static final int STAGE_1_TICKS = 80;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 80;

    private static final double SHINE_START_HEIGHT = 12.0;

    // Particles
    private static final DustParticleOptions RITUAL_PINK = new DustParticleOptions(new Vector3f(1.0f, 0.4f, 0.8f), 1.3f);
    private static final DustParticleOptions RITUAL_WHITE_PINK = new DustParticleOptions(new Vector3f(1.0f, 0.7f, 0.9f), 1.1f);
    private static final DustParticleOptions RITUAL_WHITE = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.2f);
    private static final DustParticleOptions RITUAL_PALE = new DustParticleOptions(new Vector3f(0.9f, 0.85f, 1.0f), 0.9f);

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
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.9f, 0.7f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();

        double radius = 1.2 + progress * 0.8;
        int orbitPoints = 6;

        for (int i = 0; i < orbitPoints; i++) {
            double angle = (time * 0.07) + (i * Math.PI * 2.0 / orbitPoints);
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + 0.8 + Math.sin(time * 0.08 + i) * 0.3;

            spawnParticles(world, RITUAL_PINK, x, y, z, 1, 0, 0.02, 0, 0.005);
            spawnParticles(world, RITUAL_PALE, x, y + 0.2, z, 1, 0.03, 0.03, 0.03, 0.008);
        }

        if (t % 3 == 0) {
            int circlePoints = 16;
            for (int i = 0; i < circlePoints; i++) {
                double angle = Math.PI * 2.0 * i / circlePoints;
                double x = pos.x + Math.cos(angle) * 2.0;
                double z = pos.z + Math.sin(angle) * 2.0;
                spawnParticles(world, RITUAL_PINK, x, pos.y + 0.05, z, 1, 0, 0.01, 0, 0.003);
            }
        }

        if (t % 20 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.4f, 1.2f + (float) progress * 0.3f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.5f, 1.4f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();

        int spiralPoints = (int)(6 + progress * 10);
        double spiralRadius = 1.0 + progress * 0.5;
        for (int i = 0; i < spiralPoints; i++) {
            double angle = (time * 0.09) + (i * Math.PI * 2.0 / spiralPoints);
            double heightVar = (i / (double) spiralPoints) * 2.0;
            double x = pos.x + Math.cos(angle) * spiralRadius;
            double z = pos.z + Math.sin(angle) * spiralRadius;

            spawnParticles(world, RITUAL_PINK, x, pos.y + heightVar, z, 1, 0, 0.03, 0, 0.006);
            spawnParticles(world, RITUAL_WHITE_PINK, x, pos.y + heightVar + 0.15, z, 1, 0.02, 0.02, 0.02, 0.005);
        }

        double shineY = pos.y + SHINE_START_HEIGHT;
        int shineDensity = (int)(5 + progress * 20);
        for (int i = 0; i < shineDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            spawnParticles(world, RITUAL_WHITE_PINK, pos.x + Math.cos(angle) * r, shineY + (world.random.nextDouble() - 0.5) * 1.5, pos.z + Math.sin(angle) * r, 1, 0.05, 0.08, 0.05, 0.02);
        }

        if (t % 3 == 0) {
            spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 2.0, shineY, pos.z + (world.random.nextDouble() - 0.5) * 2.0, 1, 0, 0.05, 0, 0.03);
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 8; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 2.5;
                spawnParticles(world, RITUAL_WHITE, pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r, 1, 0, 0.04, 0, 0.015);
            }
        }
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7f, 0.5f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        long time = world.getGameTime();

        double shineY = pos.y + SHINE_START_HEIGHT - (SHINE_START_HEIGHT - 2.5) * progress;
        int shineDensity = 20 + (int)(progress * 15);
        double shineRadius = 1.5 - progress * 0.8;

        for (int i = 0; i < shineDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * shineRadius;
            spawnParticles(world, RITUAL_WHITE_PINK, pos.x + Math.cos(angle) * r, shineY + (world.random.nextDouble() - 0.3) * 1.2, pos.z + Math.sin(angle) * r, 1, 0.04, 0.06, 0.04, 0.015 + progress * 0.02);
        }

        if (t % 2 == 0) {
            double beamSegments = 8;
            for (int i = 0; i < beamSegments; i++) {
                double beamT = i / beamSegments;
                double beamY = pos.y + 1.0 + (shineY - pos.y - 1.0) * beamT;
                spawnParticles(world, RITUAL_PINK, pos.x + (world.random.nextDouble() - 0.5) * 0.2, beamY, pos.z + (world.random.nextDouble() - 0.5) * 0.2, 1, 0, 0.03, 0, 0.01);
            }
        }

        if (progress > 0.7f && t % 3 == 0) {
            spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 0.5, shineY, pos.z + (world.random.nextDouble() - 0.5) * 0.5, 2, 0.1, 0.1, 0.1, 0.05);
        }

        for (int i = 0; i < 8; i++) {
            double angle = (time * 0.12) + (i * Math.PI * 2.0 / 8);
            spawnParticles(world, RITUAL_WHITE, pos.x + Math.cos(angle) * 1.2, pos.y + 1.0, pos.z + Math.sin(angle) * 1.2, 1, 0, 0.02, 0, 0.005);
        }

        if (progress > 0.95f) {
            spawnParticles(world, RITUAL_WHITE_PINK, pos.x, pos.y + 1.5, pos.z, 10, 0.5, 0.5, 0.5, 0.06);
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.5, pos.z, 1, 0, 0, 0, 0);

            if (t % 5 == 0) {
                world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.8f, false);
            }
        }
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 0.6f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.0f, false);

            spawnParticles(world, RITUAL_WHITE_PINK, pos.x, pos.y + 1.0, pos.z, 40, 1.2, 1.0, 1.2, 0.1);
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.3, 0.3, 0.3, 0);
        }

        double progress = (double) t / STAGE_4_TICKS;
        int burstCount = (int)(5 + (1.0 - progress) * 10);

        for (int i = 0; i < burstCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.8;
            double h = world.random.nextDouble() * 2.5;
            spawnParticles(world, world.random.nextFloat() < 0.5f ? RITUAL_PINK : RITUAL_WHITE_PINK, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0, 0.03, 0, 0.02);
        }

        if (t % 8 == 0) spawnParticles(world, ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 3, 0.4, 0.3, 0.4, 0.06);

        if (t % 5 == 0) world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSLOOP.get(), SoundSource.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f, false);

        if (progress > 0.75f) {
            spawnParticles(world, RITUAL_PALE, pos.x, pos.y + 1.0, pos.z, 4, 0.5, 0.4, 0.5, 0.01);
        }
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        spawnParticles(world, RITUAL_PINK, pos.x, pos.y + 1.0, pos.z, 30, 1.0, 0.8, 1.0, 0.08);
        spawnParticles(world, RITUAL_WHITE_PINK, pos.x, pos.y + 1.0, pos.z, 20, 0.8, 0.6, 0.8, 0.06);
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.1, 0.2, 0);

        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f, false);
    }
}