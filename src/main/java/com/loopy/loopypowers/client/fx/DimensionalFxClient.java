package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.power.DimensionalPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DimensionalFxClient {

    private static final Map<Integer, ClientFracture> ACTIVE_FRACTURES = new HashMap<>();

    private static final DustParticleOptions CRACK_DARK   = new DustParticleOptions(new Vector3f(0.05f, 0.15f, 0.55f), 1.8f);
    private static final DustParticleOptions CRACK_MID    = new DustParticleOptions(new Vector3f(0.25f, 0.55f, 1.0f),  1.4f);
    private static final DustParticleOptions CRACK_BRIGHT = new DustParticleOptions(new Vector3f(0.75f, 0.92f, 1.0f),  1.1f);
    private static final DustParticleOptions RIFT_WHITE   = new DustParticleOptions(new Vector3f(0.9f,  0.97f, 1.0f),  1.2f);

    private static class ClientFracture {
        List<List<Vec3>> cracks = new ArrayList<>();
        List<List<Vec3>> rings = new ArrayList<>();
        Vec3 origin;
    }

    // Triggered by the payload arriving
    public static void updateFracture(int ownerId, double x, double y, double z, long seed, boolean active) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        if (!active) {
            ACTIVE_FRACTURES.remove(ownerId);
            return;
        }

        ClientFracture frac = new ClientFracture();
        frac.origin = new Vec3(x, y, z);

        // Replicate the exact server geometry using the shared seed!
        Random rng = new Random(seed);
        double angleStep = (Math.PI * 2.0) / DimensionalPower.ULT_CRACK_COUNT;

        for (int i = 0; i < DimensionalPower.ULT_CRACK_COUNT; i++) {
            double baseAngle = angleStep * i + (rng.nextDouble() - 0.5) * angleStep * 0.6;
            frac.cracks.add(DimensionalPower.buildCrackLine(frac.origin, baseAngle, rng, client.level));
        }

        for (int i = 1; i <= DimensionalPower.ULT_RING_COUNT; i++) {
            double radius = i * DimensionalPower.ULT_RING_SPACING;
            frac.rings.add(DimensionalPower.buildRing(frac.origin, radius, client.level));
        }

        ACTIVE_FRACTURES.put(ownerId, frac);
        spawnRiftOpeningParticles(client.level, frac.origin);
    }

    // Tick hooked in LoopypowersClient.java
    public static void tick(Minecraft client) {
        if (client.isPaused() || client.level == null) return;
        ClientLevel level = client.level;
        long time = level.getGameTime();

        if (ACTIVE_FRACTURES.isEmpty()) return;

        if (time % DimensionalPower.ULT_PARTICLE_INTERVAL == 0) {
            for (ClientFracture frac : ACTIVE_FRACTURES.values()) {
                for (List<Vec3> crack : frac.cracks) {
                    spawnCrackWallParticles(level, crack);
                }
                for (List<Vec3> ring : frac.rings) {
                    spawnCrackWallParticles(level, ring);
                }
                if (frac.origin != null) {
                    spawnRiftAuraParticles(level, frac.origin, time);
                }
            }
        }
    }

    private static void spawnCrackWallParticles(ClientLevel level, List<Vec3> crack) {
        for (int i = 0; i < crack.size() - 1; i++) {
            Vec3 a = crack.get(i);
            Vec3 b = crack.get(i + 1);
            int steps = Math.max(1, (int)(a.distanceTo(b) / 0.55));

            for (int s = 0; s <= steps; s++) {
                if (level.random.nextInt(4) != 0) continue;

                double t = (double) s / steps;
                double baseX = a.x + (b.x - a.x) * t + (level.random.nextDouble() - 0.5) * 0.08;
                double baseZ = a.z + (b.z - a.z) * t + (level.random.nextDouble() - 0.5) * 0.08;
                double baseY = a.y + (b.y - a.y) * t;

                double wallHeight = DimensionalPower.ULT_WALL_PARTICLE_HEIGHT * (0.5 + level.random.nextDouble() * 0.5);

                level.addParticle(CRACK_DARK, baseX, baseY + 0.05, baseZ, 0, 0, 0);
                level.addParticle(CRACK_MID,  baseX, baseY + 0.12, baseZ, 0, 0, 0);

                for (int h = 1; h < DimensionalPower.ULT_WALL_PARTICLE_DENSITY + 2; h++) {
                    double y = baseY + (wallHeight * h / (DimensionalPower.ULT_WALL_PARTICLE_DENSITY + 2));
                    float heightFraction = (float) h / (DimensionalPower.ULT_WALL_PARTICLE_DENSITY + 2);
                    DustParticleOptions color = heightFraction < 0.35f ? CRACK_DARK : CRACK_BRIGHT;

                    double driftX = (level.random.nextDouble() - 0.5) * 0.015 * h;
                    double driftZ = (level.random.nextDouble() - 0.5) * 0.015 * h;

                    level.addParticle(color, baseX + driftX, y, baseZ + driftZ, 0, 0.02 + heightFraction * 0.03, 0);
                }

                if (level.random.nextFloat() < 0.06f) {
                    level.addParticle(RIFT_WHITE, baseX, baseY + wallHeight * 0.4, baseZ, 0, 0.06, 0);
                }

                if (level.random.nextFloat() < 0.04f) {
                    level.addParticle(ParticleTypes.END_ROD, baseX, baseY + 0.1, baseZ,
                            (level.random.nextDouble() - 0.5) * 0.04,
                            0.06 + level.random.nextDouble() * 0.06,
                            (level.random.nextDouble() - 0.5) * 0.04);
                }
            }
        }
    }

    private static void spawnRiftOpeningParticles(ClientLevel level, Vec3 pos) {
        for (int ring = 0; ring < 3; ring++) {
            double r = 1.5 + ring * 1.8;
            int ringPoints = 16 + ring * 8;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                level.addParticle(ring == 0 ? RIFT_WHITE : ring == 1 ? CRACK_BRIGHT : CRACK_MID,
                        pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r,
                        -Math.cos(angle) * (0.2 + ring * 0.05), 0.04, -Math.sin(angle) * (0.2 + ring * 0.05));
            }
        }

        addSpreadParticles(level, RIFT_WHITE,   pos, 25, 0.4, 0.6, 0.4);
        addSpreadParticles(level, CRACK_BRIGHT, pos, 18, 0.5, 0.7, 0.5);
        addSpreadParticles(level, CRACK_MID,    pos, 12, 0.6, 0.4, 0.6);
        addSpreadParticles(level, CRACK_DARK,   pos, 8,  0.7, 0.3, 0.7);

        for (int i = 0; i < 16; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = level.random.nextDouble() * 1.5;
            level.addParticle(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * r, pos.y + 0.2, pos.z + Math.sin(a) * r,
                    Math.cos(a) * 0.04, 0.12 + level.random.nextDouble() * 0.1, Math.sin(a) * 0.04);
        }
    }

    private static void addSpreadParticles(ClientLevel level, DustParticleOptions opt, Vec3 pos, int count, double sx, double sy, double sz) {
        for (int i = 0; i < count; i++) {
            level.addParticle(opt,
                    pos.x + (level.random.nextDouble() - 0.5) * sx * 2,
                    pos.y + 0.2 + (level.random.nextDouble() - 0.5) * sy * 2,
                    pos.z + (level.random.nextDouble() - 0.5) * sz * 2,
                    0, 0, 0);
        }
    }

    private static void spawnRiftAuraParticles(ClientLevel level, Vec3 pos, long time) {
        for (int ring = 0; ring < 2; ring++) {
            double speed  = ring == 0 ? 0.09 : -0.06;
            double radius = ring == 0 ? 0.7  : 1.1;
            double angle  = time * speed;
            int points    = ring == 0 ? 3    : 5;

            for (int i = 0; i < points; i++) {
                double a = angle + (i * Math.PI * 2.0 / points);
                double r = radius + Math.sin(time * 0.07 + i) * 0.15;

                level.addParticle(ring == 0 ? RIFT_WHITE : CRACK_BRIGHT,
                        pos.x + Math.cos(a) * r,
                        pos.y + 0.2 + Math.sin(time * 0.05 + i) * 0.12,
                        pos.z + Math.sin(a) * r,
                        0, 0.02, 0);
            }
        }
    }
}