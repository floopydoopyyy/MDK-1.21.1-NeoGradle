package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class NatureFxClient {

    private static final DustParticleOptions GAS_DUST = new DustParticleOptions(new Vector3f(0.12f, 0.95f, 0.18f), 1.75f);
    private static final DustParticleOptions VINE_DUST = new DustParticleOptions(new Vector3f(0.10f, 0.85f, 0.12f), 1.35f);
    private static final DustParticleOptions VINE_PINK_DUST = new DustParticleOptions(new Vector3f(0.95f, 0.35f, 0.85f), 1.05f);
    private static final DustParticleOptions CAGE_VINE_DUST = new DustParticleOptions(new Vector3f(0.10f, 0.85f, 0.12f), 1.5f);

    public static void spawnGasFx(Vec3 center, float r, float halfH, int count, int seed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        RandomSource rand = level.random;
        long now = level.getGameTime();

        // Thick core
        for (int i = 0; i < count; i++) {
            level.addParticle(GAS_DUST,
                    center.x + rand.nextGaussian() * (r * 0.9),
                    center.y + rand.nextGaussian() * (halfH * 0.9),
                    center.z + rand.nextGaussian() * (r * 0.9),
                    0, 0, 0);
        }

        // Boundary wisps
        if (((now + seed) & 1L) == 0L) {
            int wispCount = Math.max(20, count / 3);
            for (int i = 0; i < wispCount; i++) {
                level.addParticle(GAS_DUST,
                        center.x + rand.nextGaussian() * (r * 1.1),
                        center.y + rand.nextGaussian() * (halfH * 0.55),
                        center.z + rand.nextGaussian() * (r * 1.1),
                        0, 0, 0);
            }
        }

        // Occasional full volume
        if (((now + seed) % 10L) == 0L) {
            for (int i = 0; i < 120; i++) {
                level.addParticle(GAS_DUST,
                        center.x + rand.nextGaussian() * (r * 0.7),
                        center.y + rand.nextGaussian() * (halfH * 0.7),
                        center.z + rand.nextGaussian() * (r * 0.7),
                        0, 0, 0);
            }
        }
    }

    public static void spawnTetherFx(Vec3 from, Vec3 to, int seed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        RandomSource rand = level.random;
        long now = level.getGameTime();

        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = Mth.clamp((int)(len * 10), 10, 60);
        Vec3 step = delta.scale(1.0 / steps);

        Vec3 p = from;
        for (int i = 0; i <= steps; i++) {
            DustParticleOptions eff = ((now + seed + i) % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;
            level.addParticle(eff,
                    p.x + rand.nextGaussian() * 0.02,
                    p.y + rand.nextGaussian() * 0.02,
                    p.z + rand.nextGaussian() * 0.02,
                    0, 0, 0);
            p = p.add(step);
        }

        // "Anchor" puff
        if ((now & 3L) == 0L) {
            for (int i = 0; i < 8; i++) {
                level.addParticle(VINE_DUST,
                        from.x + rand.nextGaussian() * 0.20,
                        from.y + 0.15 + rand.nextGaussian() * 0.10,
                        from.z + rand.nextGaussian() * 0.20,
                        0, 0, 0);
            }
            for (int i = 0; i < 2; i++) {
                level.addParticle(VINE_PINK_DUST,
                        from.x + rand.nextGaussian() * 0.20,
                        from.y + 0.15 + rand.nextGaussian() * 0.10,
                        from.z + rand.nextGaussian() * 0.20,
                        0, 0, 0);
            }
        }
    }

    public static void spawnCageFx(int cx, int cy, int cz, int radius, int thickness) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        int points = 80;
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());

        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            for (int t = 0; t < thickness; t++) {
                double rNow = radius - t + (level.random.nextDouble() * 0.5 - 0.25);
                double x = cx + Math.cos(a) * rNow;
                double z = cz + Math.sin(a) * rNow;

                // Raycast down visually to find ground level
                int y = cy + 2;
                while (y > cy - 10 && level.getBlockState(new BlockPos((int)x, y, (int)z)).isAir()) {
                    y--;
                }

                // Erupt upwards
                level.addParticle(dirt, x, y + 1.0, z, 0, 0.2 + level.random.nextDouble() * 0.3, 0);
                level.addParticle(CAGE_VINE_DUST, x, y + 1.0, z, 0, 0.3 + level.random.nextDouble() * 0.4, 0);
                if (level.random.nextFloat() < 0.3f) {
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y + 1.0, z, 0, 0.1, 0);
                }
            }
        }
    }
}