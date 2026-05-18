package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side FX for the Lightning Maelstrom ultimate.
 *
 * The cloud is built in four distinct structural layers, each spawned
 * every 6 ticks. A slow rotation offset driven by stormTicks ties
 * the whole structure together so it visibly turns overhead.
 *
 *  Layer 1  CANOPY      (y+8.5)  — light CLOUD wisps on the very top
 *  Layer 2  CROWN EDGE  (y+7.5)  — dense dark ring at exactly STORM_RADIUS
 *                                   giving the cloud a hard defined boundary
 *  Layer 3  BODY        (y+6–7)  — thick dark mass filling the disc interior
 *  Layer 4  UNDERBELLY  (y+5–6)  — BOLT_YELLOW glow + ELECTRIC_SPARK drops
 *                                   hanging below the cloud toward the ground
 *
 * On activation a radius-reveal ring expands outward to the full 15-block
 * boundary so nearby players immediately see the zone size.
 */
public class StormCloudFxClient {

    // Must match LightningPower.STORM_RADIUS exactly
    private static final double STORM_RADIUS = 15.0;

    // Particle colours (mirror the server-side constants)
    private static final DustParticleOptions BOLT_YELLOW =
            new DustParticleOptions(new Vector3f(1.00f, 0.88f, 0.05f), 1.4f);
    private static final DustParticleOptions BOLT_WHITE =
            new DustParticleOptions(new Vector3f(1.00f, 1.00f, 0.82f), 1.6f);
    private static final DustParticleOptions STORM_BLACK =
            new DustParticleOptions(new Vector3f(0.08f, 0.08f, 0.10f), 1.8f);
    private static final DustParticleOptions STORM_GREY =
            new DustParticleOptions(new Vector3f(0.20f, 0.20f, 0.22f), 1.5f);

    // ----------------------------------------------------------------
    // PUBLIC ENTRY POINT
    // ----------------------------------------------------------------

    public static void onStormCloudTick(int entityId, int stormTicks, boolean isActivation) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        Vec3 center = entity.position();

        // Rotation offset — 300 ticks total storm duration, one full rotation
        // every ~200 ticks gives a slow, ominous turn.
        double rotAngle = stormTicks * 0.031;

        if (isActivation) {
            spawnActivationBurst(level, center, rotAngle);
        } else {
            spawnCloudLayers(level, center, rotAngle, stormTicks);
        }
    }

    // ----------------------------------------------------------------
    // LAYERED CLOUD (called every 6 ticks while ult is active)
    // ----------------------------------------------------------------

    private static void spawnCloudLayers(ClientLevel level, Vec3 center,
                                         double rotAngle, int stormTicks) {
        spawnCanopyTop(level, center, rotAngle);
        spawnCrownEdge(level, center, rotAngle);
        spawnCloudBody(level, center, rotAngle, stormTicks);
        spawnUnderbelly(level, center, rotAngle, stormTicks);
    }

    /**
     * Layer 1 — CANOPY (y+8.5)
     * Light CLOUD particles on the very top of the formation.
     * Sparse so they don't obscure the dark mass below.
     */
    private static void spawnCanopyTop(ClientLevel level, Vec3 center, double rotAngle) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = rotAngle + (2 * Math.PI / points) * i;

            // Two radii — mid-cloud and near-edge — for an uneven natural top
            for (double r : new double[]{ STORM_RADIUS * 0.45, STORM_RADIUS * 0.80 }) {
                double x = center.x + Math.cos(a) * r;
                double z = center.z + Math.sin(a) * r;
                double y = center.y + 8.5 + (Math.random() - 0.5) * 0.6;

                level.addParticle(ParticleTypes.CLOUD,
                        x, y, z,
                        (Math.random() - 0.5) * 0.008, 0.002, (Math.random() - 0.5) * 0.008);
            }
        }
    }

    /**
     * Layer 2 — CROWN EDGE (y+7–8)
     * The most important visual layer: a dense ring of dark cloud particles
     * sitting exactly at STORM_RADIUS. This gives the cloud a hard, readable
     * boundary so the zone size is immediately clear.
     * A second inner ring sits just inside to give the edge some depth.
     */
    private static void spawnCrownEdge(ClientLevel level, Vec3 center, double rotAngle) {
        int outerPoints = 32;
        for (int i = 0; i < outerPoints; i++) {
            double a = rotAngle + (2 * Math.PI / outerPoints) * i;

            for (double yOff : new double[]{ 7.0, 7.6, 8.1 }) {
                double x = center.x + Math.cos(a) * (STORM_RADIUS - 0.5 + Math.random() * 1.2);
                double z = center.z + Math.sin(a) * (STORM_RADIUS - 0.5 + Math.random() * 1.2);
                double y = center.y + yOff + (Math.random() - 0.5) * 0.5;

                DustParticleOptions col = (Math.random() < 0.65) ? STORM_BLACK : STORM_GREY;
                level.addParticle(col, x, y, z,
                        0, (Math.random() - 0.5) * 0.004, 0);
            }

            if (i % 2 == 0) {
                double x = center.x + Math.cos(a) * (STORM_RADIUS * 0.95);
                double z = center.z + Math.sin(a) * (STORM_RADIUS * 0.95);
                level.addParticle(ParticleTypes.CLOUD,
                        x, center.y + 7.4, z,
                        0, 0.003, 0);
            }
        }

        int innerPoints = 20;
        for (int i = 0; i < innerPoints; i++) {
            double a = -rotAngle + (2 * Math.PI / innerPoints) * i; // counter-rotate for depth
            double x = center.x + Math.cos(a) * (STORM_RADIUS * 0.82);
            double z = center.z + Math.sin(a) * (STORM_RADIUS * 0.82);
            level.addParticle(STORM_BLACK,
                    x, center.y + 6.8 + Math.random() * 0.8, z,
                    0, 0.002, 0);
        }
    }

    /**
     * Layer 3 — BODY (y+6–7.5)
     * Fills the interior of the cloud disc with overlapping dark puffs.
     * Uses three concentric rings at different radii and heights so the
     * mass looks thick and volumetric rather than a flat disc.
     * Rotates slightly faster than the edge for an internal turbulence look.
     */
    private static void spawnCloudBody(ClientLevel level, Vec3 center,
                                       double rotAngle, int stormTicks) {
        double fastRot = rotAngle * 1.3; // body swirls a little faster

        // Ring A
        int pointsA = 18;
        for (int i = 0; i < pointsA; i++) {
            double a = fastRot + (2 * Math.PI / pointsA) * i;
            double r = STORM_RADIUS * 0.58 + (Math.random() - 0.5) * 2.0;
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            double y = center.y + 6.5 + Math.random() * 0.9;

            DustParticleOptions col = (Math.random() < 0.5) ? STORM_BLACK : STORM_GREY;
            level.addParticle(col, x, y, z, 0, 0.002, 0);

            if (Math.random() < 0.25) {
                level.addParticle(ParticleTypes.CLOUD, x, y + 0.3, z,
                        0, 0.003, 0);
            }
        }

        // Ring B
        int pointsB = 12;
        for (int i = 0; i < pointsB; i++) {
            double a = -fastRot + (2 * Math.PI / pointsB) * i; // reverse inner swirl
            double r = STORM_RADIUS * 0.30 + (Math.random() - 0.5) * 1.5;
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            double y = center.y + 6.2 + Math.random() * 1.2;

            level.addParticle(STORM_BLACK, x, y, z, 0, 0.003, 0);
        }

        // occasional other
        if (stormTicks % 12 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = Math.random() * Math.PI * 2;
                double r = STORM_RADIUS * (0.2 + Math.random() * 0.6);
                level.addParticle(STORM_GREY,
                        center.x + Math.cos(a) * r,
                        center.y + 7.0 + Math.random() * 1.0,
                        center.z + Math.sin(a) * r,
                        0, 0.004, 0);
            }
        }
    }

    /**
     * Layer 4 — UNDERBELLY (y+5–6.5)
     * The most dramatic layer: sits below the dark mass and makes the
     * cloud look electrically charged. Two sub-layers:
     *   a) BOLT_YELLOW glow patches — the cloud is lit from within
     *   b) ELECTRIC_SPARK drops falling straight down toward the ground —
     *      the visual threat signal that a strike is coming
     */
    private static void spawnUnderbelly(ClientLevel level, Vec3 center,
                                        double rotAngle, int stormTicks) {
        int glowPoints = 14;
        for (int i = 0; i < glowPoints; i++) {
            double a = rotAngle * 0.7 + (2 * Math.PI / glowPoints) * i;
            double r = STORM_RADIUS * (0.20 + Math.random() * 0.65);
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            double y = center.y + 5.6 + Math.random() * 0.9;

            level.addParticle(BOLT_YELLOW, x, y, z,
                    (Math.random() - 0.5) * 0.015, -(Math.random() * 0.008), (Math.random() - 0.5) * 0.015);
        }

        if (stormTicks % 6 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = Math.random() * Math.PI * 2;
                double r = STORM_RADIUS * (0.15 + Math.random() * 0.5);
                level.addParticle(BOLT_WHITE,
                        center.x + Math.cos(a) * r,
                        center.y + 5.8 + Math.random() * 0.6,
                        center.z + Math.sin(a) * r,
                        0, -0.005, 0);
            }
        }

        int sparkDrops = 10;
        for (int i = 0; i < sparkDrops; i++) {
            double a = Math.random() * Math.PI * 2;
            // Weighted toward inner area where lightning tends to land
            double r = STORM_RADIUS * (0.1 + Math.random() * Math.random() * 0.75);
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            double y = center.y + 5.2 + Math.random() * 1.0;

            // Slight downward drift so they look like they're falling
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x, y, z,
                    (Math.random() - 0.5) * 0.02, -(0.02 + Math.random() * 0.04), (Math.random() - 0.5) * 0.02);
        }
    }

    // ----------------------------------------------------------------
    // ACTIVATION BURST
    // ----------------------------------------------------------------

    private static void spawnActivationBurst(ClientLevel level, Vec3 center, double rotAngle) {

        // Three expanding rings at different radii and heights
        // they trace the full disc boundary outward
        double[][] rings = {
                { STORM_RADIUS * 0.45, center.y + 7.0, 20 },  // inner
                { STORM_RADIUS * 0.75, center.y + 7.2, 28 },  // mid
                { STORM_RADIUS,        center.y + 7.5, 36 },  // outer — the hard boundary
        };

        for (double[] ring : rings) {
            double r      = ring[0];
            double y      = ring[1];
            int    points = (int) ring[2];

            for (int i = 0; i < points; i++) {
                double a = (2 * Math.PI / points) * i;

                // expanding ring
                double outSpeed = 0.22 * (r / STORM_RADIUS); // faster at the edge
                double vx = Math.cos(a) * outSpeed;
                double vz = Math.sin(a) * outSpeed;

                //change particles randomly
                int mod = i % 3;
                if (mod == 0) {
                    level.addParticle(ParticleTypes.CLOUD,
                            center.x + Math.cos(a) * 0.5, y, center.z + Math.sin(a) * 0.5,
                            vx, 0.005, vz);
                } else if (mod == 1) {
                    level.addParticle(STORM_BLACK,
                            center.x + Math.cos(a) * 0.5, y, center.z + Math.sin(a) * 0.5,
                            vx, 0.003, vz);
                } else {
                    level.addParticle(BOLT_YELLOW,
                            center.x + Math.cos(a) * 0.5, y, center.z + Math.sin(a) * 0.5,
                            vx, 0.008, vz);
                }
            }
        }

        // central stuff
        for (int i = 0; i < 30; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = Math.random() * 2.0;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    center.x + Math.cos(a) * r,
                    center.y + 0.5 + Math.random() * 2.0,
                    center.z + Math.sin(a) * r,
                    (Math.random() - 0.5) * 0.06,
                    0.12 + Math.random() * 0.18,  // strongly upward
                    (Math.random() - 0.5) * 0.06);
        }

        // Initial full-radius cloud band to show boundary
        // (before the per-tick layers have had time to build up)
        int edgePoints = 48;
        for (int i = 0; i < edgePoints; i++) {
            double a = rotAngle + (2 * Math.PI / edgePoints) * i;
            double x = center.x + Math.cos(a) * (STORM_RADIUS - 0.8 + Math.random() * 1.6);
            double z = center.z + Math.sin(a) * (STORM_RADIUS - 0.8 + Math.random() * 1.6);
            double y = center.y + 6.5 + Math.random() * 2.0;

            DustParticleOptions col = (Math.random() < 0.6) ? STORM_BLACK : STORM_GREY;
            level.addParticle(col, x, y, z, 0, 0.003, 0);

            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.CLOUD, x, y + 0.4, z, 0, 0.004, 0);
            }
        }
    }
}