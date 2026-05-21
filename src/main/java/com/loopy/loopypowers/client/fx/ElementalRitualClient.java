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

public class ElementalRitualClient {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;
    private static final double COLUMN_HEIGHT = 10.0;

    // Particles
    private static final DustParticleOptions FIRE_ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.0f), 1.5f);
    private static final DustParticleOptions FIRE_RED = new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.0f), 1.3f);
    private static final DustParticleOptions ICE_BLUE = new DustParticleOptions(new Vector3f(0.35f, 0.8f, 1.0f), 1.4f);
    private static final DustParticleOptions ICE_WHITE = new DustParticleOptions(new Vector3f(0.8f, 0.95f, 1.0f), 1.1f);
    private static final DustParticleOptions LIGHTNING_YELLOW = new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.1f), 1.5f);
    private static final DustParticleOptions LIGHTNING_WHITE = new DustParticleOptions(new Vector3f(0.9f, 0.95f, 1.0f), 1.2f);
    private static final DustParticleOptions EARTH_BROWN = new DustParticleOptions(new Vector3f(0.45f, 0.28f, 0.08f), 1.4f);
    private static final DustParticleOptions EARTH_GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.55f, 0.1f), 1.2f);
    private static final DustParticleOptions WATER_TEAL = new DustParticleOptions(new Vector3f(0.1f, 0.6f, 0.8f), 1.3f);
    private static final DustParticleOptions WATER_CYAN = new DustParticleOptions(new Vector3f(0.4f, 0.9f, 0.9f), 1.0f);
    private static final DustParticleOptions AIR_PALE = new DustParticleOptions(new Vector3f(0.85f, 0.95f, 1.0f), 0.9f);

    private static final DustParticleOptions[] COL_PRIMARY = { FIRE_ORANGE, ICE_BLUE, LIGHTNING_YELLOW, WATER_TEAL };
    private static final DustParticleOptions[] COL_SECONDARY = { FIRE_RED, ICE_WHITE, LIGHTNING_WHITE, WATER_CYAN };

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

    /* ============================================================
       HELPER TO REPLICATE ServerLevel.sendParticles LOGIC
       ============================================================ */
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

    /* ============================================================
       STAGE LOGIC
       ============================================================ */

    private static void tickStage1(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 1.0f, 0.7f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.0f, false);
        }

        double progress = (double) t / STAGE_1_TICKS;
        long time = world.getGameTime();

        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2.0 * i / 6 + time * 0.02;
                double r = 2.5 + Math.sin(time * 0.05 + i) * 0.3;
                spawnParticles(world, EARTH_BROWN, pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r, 1, 0.1, 0.04, 0.1, 0.005);
                spawnParticles(world, EARTH_GREEN, pos.x + Math.cos(angle + 0.3) * r, pos.y + 0.1, pos.z + Math.sin(angle + 0.3) * r, 1, 0.05, 0.05, 0.05, 0.003);
            }
        }

        int waterPoints = (int)(4 + progress * 6);
        for (int i = 0; i < waterPoints; i++) {
            double angle = -(time * 0.06) + (i * Math.PI * 2.0 / waterPoints);
            double r = 1.6;
            double y = pos.y + 0.6 + Math.sin(time * 0.07 + i) * 0.25;
            spawnParticles(world, WATER_TEAL, pos.x + Math.cos(angle) * r, y, pos.z + Math.sin(angle) * r, 1, 0, 0.015, 0, 0.004);
            if (t % 4 == 0) {
                spawnParticles(world, WATER_CYAN, pos.x + Math.cos(angle) * r, y + 0.15, pos.z + Math.sin(angle) * r, 1, 0.02, 0.02, 0.02, 0.006);
            }
        }

        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.2;
                double h = 1.5 + world.random.nextDouble() * 2.5;
                spawnParticles(world, AIR_PALE, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0.04, 0.06, 0.04, 0.01);
            }
        }

        if (t % 30 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 0.3f, 0.9f + (float) progress * 0.2f, false);
        }
    }

    private static void tickStage2(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.6f, 0.9f, false);
        }

        double progress = (double) t / STAGE_2_TICKS;
        long time = world.getGameTime();
        double columnRadius = 3.5;

        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx = pos.x + Math.cos(angle) * columnRadius;
            double cz = pos.z + Math.sin(angle) * columnRadius;
            double columnHeight = COLUMN_HEIGHT * progress;
            int colSteps = (int)(columnHeight / 0.5) + 1;

            for (int s = 0; s < colSteps; s++) {
                double y = pos.y + s * 0.5;
                if (t % 3 == 0) {
                    spawnParticles(world, COL_PRIMARY[col], cx + (world.random.nextDouble() - 0.5) * 0.4, y, cz + (world.random.nextDouble() - 0.5) * 0.4, 1, 0.04, 0.03, 0.04, 0.008);
                }
                if (t % 5 == 0 && s % 2 == 0) {
                    spawnParticles(world, COL_SECONDARY[col], cx, y + 0.15, cz, 1, 0.06, 0.02, 0.06, 0.006);
                }
            }
            if (columnHeight > 2.0) {
                spawnParticles(world, COL_PRIMARY[col], cx, pos.y + columnHeight, cz, 2, 0.3, 0.2, 0.3, 0.02);
            }
        }

        if (t % 4 == 0) {
            double apexY = pos.y + COLUMN_HEIGHT * progress;
            for (int i = 0; i < 5; i++) {
                double a = (time * 0.12) + (i * Math.PI * 2.0 / 5);
                spawnParticles(world, AIR_PALE, pos.x + Math.cos(a) * (columnRadius * 0.6), apexY, pos.z + Math.sin(a) * (columnRadius * 0.6), 1, 0.05, 0.04, 0.05, 0.01);
            }
        }

        if (t == 15) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.1f, false);
        if (t == 30) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.POWDER_SNOW_FALL, SoundSource.PLAYERS, 0.5f, 0.8f, false);
        if (t == 45) world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.5f, 1.5f, false);
    }

    private static void tickStage3(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.9f, false);
        }

        double progress = (double) t / STAGE_3_TICKS;
        double surgeRadius = 3.5 - progress * 2.5;

        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx = pos.x + Math.cos(angle) * surgeRadius;
            double cz = pos.z + Math.sin(angle) * surgeRadius;

            int colSteps = (int)(COLUMN_HEIGHT * (0.6 + progress * 0.4));
            for (int s = 0; s < colSteps; s++) {
                if (t % 2 == 0) {
                    spawnParticles(world, COL_PRIMARY[col], cx + (world.random.nextDouble() - 0.5) * (0.4 + progress * 0.6), pos.y + s * 0.45, cz + (world.random.nextDouble() - 0.5) * (0.4 + progress * 0.6), 1, 0.06, 0.04, 0.06, 0.012);
                }
            }

            if (t % 5 == 0) {
                Vec3 toPlayer = pos.add(0, 1, 0).subtract(cx, pos.y + COLUMN_HEIGHT * 0.5, cz).normalize().scale(0.08 + progress * 0.06);
                // For directional particles, count is 0, velocity goes in dx/dy/dz
                spawnParticles(world, COL_SECONDARY[col], cx, pos.y + COLUMN_HEIGHT * 0.5, cz, 0, toPlayer.x, toPlayer.y + 0.02, toPlayer.z, 1.0);
            }
        }

        long time = world.getGameTime();
        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double a = (time * 0.1) + (col * Math.PI * 2.0 / COL_PRIMARY.length);
            double r = 1.4 - progress * 0.5;
            spawnParticles(world, COL_PRIMARY[col], pos.x + Math.cos(a) * r, pos.y + 1.0, pos.z + Math.sin(a) * r, 1, 0.03, 0.03, 0.03, 0.01);
        }

        if (t % 18 == 0) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, (float)(0.3 + progress * 0.4), 0.8f + (float) progress * 0.3f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, (float)(0.3 + progress * 0.3), 1.0f, false);
        }
    }

    private static void tickStage4(Level world, Player player, int t) {
        Vec3 pos = player.position();
        if (t == 1) {
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3f, 0.6f, false);
            world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.0f, false);

            for (DustParticleOptions col : COL_PRIMARY) {
                spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 12, 1.2, 1.0, 1.2, 0.1);
            }
            spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.2, 0.2, 0.2, 0);
        }

        double progress = (double) t / STAGE_4_TICKS;
        int burstCount = (int)(8 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            DustParticleOptions col = COL_PRIMARY[i % COL_PRIMARY.length];
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.8;
            double h = world.random.nextDouble() * 2.5;
            spawnParticles(world, col, pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r, 1, 0, 0.03, 0, 0.018);
        }

        if (t % 4 == 0) {
            spawnParticles(world, ParticleTypes.FLAME, pos.x + (world.random.nextDouble() - 0.5) * 1.5, pos.y + world.random.nextDouble() * 2.0, pos.z + (world.random.nextDouble() - 0.5) * 1.5, 1, 0.08, 0.08, 0.08, 0.025);
            spawnParticles(world, ParticleTypes.SNOWFLAKE, pos.x + (world.random.nextDouble() - 0.5) * 2.0, pos.y + 1.0 + world.random.nextDouble() * 1.5, pos.z + (world.random.nextDouble() - 0.5) * 2.0, 1, 0.04, 0.04, 0.04, 0.015);
        }
        if (t % 5 == 0) {
            spawnParticles(world, ParticleTypes.END_ROD, pos.x + (world.random.nextDouble() - 0.5) * 1.2, pos.y + 0.5 + world.random.nextDouble() * 1.8, pos.z + (world.random.nextDouble() - 0.5) * 1.2, 1, 0.06, 0.1, 0.06, 0.03);
            world.playLocalSound(pos.x, pos.y, pos.z, ModSounds.DARKNESSLOOP.get(), SoundSource.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f, false);
        }

        if (progress > 0.8f) {
            for (DustParticleOptions sec : COL_SECONDARY) {
                spawnParticles(world, sec, pos.x, pos.y + 1.2, pos.z, 1, 0.4, 0.3, 0.4, 0.006);
            }
        }
    }

    private static void onComplete(Level world, Player player) {
        Vec3 pos = player.position();
        for (DustParticleOptions col : COL_PRIMARY) {
            spawnParticles(world, col, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.09);
        }
        spawnParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.1, 0.2, 0);
        world.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f, false);
    }
}