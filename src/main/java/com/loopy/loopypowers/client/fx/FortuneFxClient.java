package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class FortuneFxClient {

    public static void spawnDuelBeam(Vec3 start, Vec3 end) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.random;

        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3 dir = delta.scale(1.0 / len);
        int steps = Mth.clamp((int)(len / 0.35), 8, 120);
        Vec3 p = start;

        for (int i = 0; i <= steps; i++) {
            double ox = random.nextGaussian() * 0.02;
            double oy = random.nextGaussian() * 0.02;
            double oz = random.nextGaussian() * 0.02;
            level.addParticle(ParticleTypes.ENCHANT, p.x + ox, p.y + oy, p.z + oz, 0, 0, 0);

            if ((i & 3) == 0) {
                double cx = random.nextGaussian() * 0.02;
                double cy = random.nextGaussian() * 0.02;
                double cz = random.nextGaussian() * 0.02;
                level.addParticle(ParticleTypes.CRIT, p.x + cx, p.y + cy, p.z + cz, 0, 0, 0);
            }
            p = p.add(dir.scale(len / steps));
        }
    }

    public static void spawnDuelTether(int entityAId, int entityBId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.random;

        Entity a = level.getEntity(entityAId);
        Entity b = level.getEntity(entityBId);
        if (a == null || b == null) return;

        Vec3 start = a.position().add(0, a.getBbHeight() * 0.65, 0);
        Vec3 end = b.position().add(0, b.getBbHeight() * 0.65, 0);

        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3 dir = delta.scale(1.0 / len);
        int steps = Mth.clamp((int)(len / 0.45), 10, 90);

        Vec3 p = start;
        for (int i = 0; i <= steps; i++) {
            double ox = random.nextGaussian() * 0.03;
            double oy = random.nextGaussian() * 0.03;
            double oz = random.nextGaussian() * 0.03;
            level.addParticle(ParticleTypes.ENCHANT, p.x + ox, p.y + oy, p.z + oz, 0, 0, 0);
            p = p.add(dir.scale(len / steps));
        }

        for (int j = 0; j < 3; j++) {
            level.addParticle(ParticleTypes.ENCHANT, start.x + random.nextGaussian() * 0.15, start.y + random.nextGaussian() * 0.15, start.z + random.nextGaussian() * 0.15, 0, 0, 0);
            level.addParticle(ParticleTypes.ENCHANT, end.x + random.nextGaussian() * 0.15, end.y + random.nextGaussian() * 0.15, end.z + random.nextGaussian() * 0.15, 0, 0, 0);
        }
    }

    public static void spawnDuelAura(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.random;

        Entity e = level.getEntity(entityId);
        if (e == null) return;

        Vec3 p = e.position().add(0, e.getBbHeight() * 0.65, 0);
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ENCHANT, p.x + random.nextGaussian() * 0.18, p.y + random.nextGaussian() * 0.25, p.z + random.nextGaussian() * 0.18, 0, 0, 0);
        }
    }

    public static void spawnHouseRoofFx(int cx, int cz, double y, int radius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.random;

        int particles = 28;
        for (int i = 0; i < particles; i++) {
            double x = cx + 0.5 + (random.nextDouble() * 2 - 1) * radius;
            double z = cz + 0.5 + (random.nextDouble() * 2 - 1) * radius;
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0, 0);
        }
    }
}