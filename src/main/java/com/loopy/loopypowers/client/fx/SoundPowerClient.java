package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SoundPowerClient {

    private static final int PULL_POINTS = 90;
    private static final int BLAST_POINTS = 120;

    private static final int PULL_VIZ_STEPS = 8;
    private static final int BLAST_VIZ_STEPS = 8;

    private static final double BD_PULL_RADIUS = 10.0;
    private static final double BD_FINAL_RADIUS = 7.0;

    private static final double ULT_PARTICLE_STEP = 0.55;

    /* ============================================================
       GENERAL
       ============================================================ */

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    /* ============================================================
       SPHERE HELPERS
       ============================================================ */

    public static void spawnSphereShell(
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle,
            double yOffset,
            long seed
    ) {
        ClientLevel level = level();
        if (level == null) return;

        RandomSource random = RandomSource.create(seed);

        for (int i = 0; i < points; i++) {

            double u = random.nextDouble();
            double v = random.nextDouble();

            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            double jitter = 0.12;

            double px = center.x + sx * radius + (random.nextDouble() - 0.5) * jitter;
            double py = center.y + yOffset + sy * radius + (random.nextDouble() - 0.5) * jitter;
            double pz = center.z + sz * radius + (random.nextDouble() - 0.5) * jitter;

            level.addParticle(
                    particle,
                    px, py, pz,
                    0.0, 0.0, 0.0
            );
        }
    }

    /* ============================================================
       BASS DROP
       ============================================================ */

    public static void spawnPullContractingSphere(Vec3 center, long seed) {
        int shells = 10;

        for (int s = 0; s < shells; s++) {

            double t = s / (double) (shells - 1);

            double radius = Mth.lerp(
                    t,
                    BD_PULL_RADIUS,
                    5.2
            );

            spawnSphereShell(
                    center,
                    radius,
                    120,
                    ParticleTypes.SCULK_CHARGE_POP,
                    0.9,
                    seed + s
            );
        }
    }

    public static void spawnFinalExpandingSphere(Vec3 center, long seed) {
        int shells = 10;

        for (int s = 0; s < shells; s++) {

            double t = s / (double) (shells - 1);

            double radius = Mth.lerp(
                    t,
                    1.0,
                    BD_FINAL_RADIUS
            );

            spawnSphereShell(
                    center,
                    radius,
                    150,
                    ParticleTypes.SCULK_CHARGE_POP,
                    0.9,
                    seed + s
            );
        }
    }

    public static void spawnBassGroundRing(
            Vec3 center,
            double radius,
            long seed
    ) {
        ClientLevel level = level();
        if (level == null) return;

        RandomSource random = RandomSource.create(seed);

        double y = center.y + 0.10;

        for (int i = 0; i < 36; i++) {

            double angle = (Math.PI * 2.0) * (i / 36.0);

            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;

            double jitter = 0.06;

            x += (random.nextDouble() - 0.5) * jitter;
            z += (random.nextDouble() - 0.5) * jitter;

            level.addParticle(
                    ParticleTypes.SCULK_CHARGE_POP,
                    x, y, z,
                    0.0, 0.0, 0.0
            );
        }
    }

    public static void animateBassPullSphere(
            Vec3 center,
            int step,
            long seed
    ) {

        double t = (PULL_VIZ_STEPS <= 1)
                ? 1.0
                : (step / (double) (PULL_VIZ_STEPS - 1));

        t = t * t;

        double radius = Mth.lerp(
                t,
                BD_PULL_RADIUS,
                2.6
        );

        spawnSphereShell(
                center,
                radius,
                PULL_POINTS,
                ParticleTypes.SCULK_CHARGE_POP,
                0.0,
                seed + step
        );

        if ((step & 1) == 0) {
            spawnSphereShell(
                    center,
                    radius * 0.55,
                    22,
                    ParticleTypes.SCULK_SOUL,
                    0.0,
                    seed + 500 + step
            );
        }
    }

    public static void animateBassBlastSphere(
            Vec3 center,
            int step,
            long seed
    ) {

        double t = (BLAST_VIZ_STEPS <= 1)
                ? 1.0
                : (step / (double) (BLAST_VIZ_STEPS - 1));

        double ease = 1.0 - Math.pow(1.0 - t, 2.0);

        double radius = Mth.lerp(
                ease,
                1.0,
                BD_FINAL_RADIUS * 1.5
        );

        spawnSphereShell(
                center,
                radius,
                BLAST_POINTS,
                ParticleTypes.SCULK_CHARGE_POP,
                0.0,
                seed + step
        );

        if ((step & 1) == 0) {
            spawnBassGroundRing(
                    center,
                    radius,
                    seed + 1000 + step
            );
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    public static void spawnUltimateBeam(
            Vec3 start,
            Vec3 end,
            long seed
    ) {

        ClientLevel level = level();
        if (level == null) return;

        RandomSource random = RandomSource.create(seed);

        Vec3 delta = end.subtract(start);

        double length = delta.length();

        if (length < 0.01) return;

        int steps = Mth.clamp(
                (int) (length / ULT_PARTICLE_STEP),
                10,
                220
        );

        Vec3 step = delta.scale(1.0 / steps);

        Vec3 pos = start;

        for (int i = 0; i <= steps; i++) {

            level.addParticle(
                    ParticleTypes.SONIC_BOOM,
                    pos.x,
                    pos.y,
                    pos.z,
                    0.0,
                    0.0,
                    0.0
            );

            if ((i & 1) == 0) {

                level.addParticle(
                        ParticleTypes.SCULK_CHARGE_POP,
                        pos.x + (random.nextDouble() - 0.5) * 0.18,
                        pos.y + (random.nextDouble() - 0.5) * 0.18,
                        pos.z + (random.nextDouble() - 0.5) * 0.18,
                        0.0,
                        0.0,
                        0.0
                );

                level.addParticle(
                        ParticleTypes.SCULK_CHARGE_POP,
                        pos.x + (random.nextDouble() - 0.5) * 0.18,
                        pos.y + (random.nextDouble() - 0.5) * 0.18,
                        pos.z + (random.nextDouble() - 0.5) * 0.18,
                        0.0,
                        0.0,
                        0.0
                );

                level.addParticle(
                        ParticleTypes.SCULK_CHARGE_POP,
                        pos.x + (random.nextDouble() - 0.5) * 0.18,
                        pos.y + (random.nextDouble() - 0.5) * 0.18,
                        pos.z + (random.nextDouble() - 0.5) * 0.18,
                        0.0,
                        0.0,
                        0.0
                );

            } else {

                level.addParticle(
                        ParticleTypes.SCULK_SOUL,
                        pos.x + (random.nextDouble() - 0.5) * 0.10,
                        pos.y + (random.nextDouble() - 0.5) * 0.10,
                        pos.z + (random.nextDouble() - 0.5) * 0.10,
                        0.0,
                        0.0,
                        0.0
                );
            }

            pos = pos.add(step);
        }

        level.addParticle(
                ParticleTypes.EXPLOSION_EMITTER,
                end.x,
                end.y,
                end.z,
                0.0,
                0.0,
                0.0
        );
    }

    /* ============================================================
       SMALL FX
       ============================================================ */

    public static void spawnResonanceBurst(Vec3 pos) {

        ClientLevel level = level();
        if (level == null) return;

        level.addParticle(
                ParticleTypes.SONIC_BOOM,
                pos.x,
                pos.y + 0.6,
                pos.z,
                0.0,
                0.0,
                0.0
        );

        for (int i = 0; i < 6; i++) {

            level.addParticle(
                    ParticleTypes.EXPLOSION,
                    pos.x,
                    pos.y + 0.2,
                    pos.z,
                    (level.random.nextDouble() - 0.5) * 0.15,
                    level.random.nextDouble() * 0.08,
                    (level.random.nextDouble() - 0.5) * 0.15
            );
        }
    }

    public static void spawnResonatedProc(Vec3 pos, long seed) {

        spawnSphereShell(
                pos,
                0.45,
                12,
                ParticleTypes.SCULK_SOUL,
                1.0,
                seed
        );
    }

    public static void spawnBassPulse(Vec3 pos) {

        ClientLevel level = level();
        if (level == null) return;

        level.addParticle(
                ParticleTypes.SONIC_BOOM,
                pos.x,
                pos.y + 1.0,
                pos.z,
                0.0,
                0.0,
                0.0
        );
    }

    /* ============================================================
       BLOCK FX
       ============================================================ */

    public static void spawnBlockBurst(
            BlockPos pos,
            BlockState state,
            long seed
    ) {

        ClientLevel level = level();
        if (level == null) return;

        RandomSource random = RandomSource.create(seed);

        for (int i = 0; i < 12; i++) {

            double vx = (random.nextDouble() - 0.5) * 0.25;
            double vy = random.nextDouble() * 0.15;
            double vz = (random.nextDouble() - 0.5) * 0.25;

            level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5,
                    pos.getY() + 0.75,
                    pos.getZ() + 0.5,
                    vx,
                    vy,
                    vz
            );
        }

        for (int i = 0; i < 2; i++) {

            level.addParticle(
                    ParticleTypes.CRIT,
                    pos.getX() + 0.5,
                    pos.getY() + 1.1,
                    pos.getZ() + 0.5,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }
}