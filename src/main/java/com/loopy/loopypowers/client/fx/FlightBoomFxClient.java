package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side particle FX for the Sonic Boom ultimate (FlightPower).
 *
 *  Windup  – a ring of cloud particles forms behind the player, drifting
 *            inward as if air is being "sucked" into them for the launch.
 *  Dash    – an expanding cylindrical wake of cloud particles traces the
 *            tunnel the player punches through the air each tick.
 *  Impact  – a flat ground-level shockwave ring + an omnidirectional cloud
 *            burst at the collision point.
 */
public class FlightBoomFxClient {

    // ----------------------------------------------------------------
    // WINDUP  (called every 2 ticks while boomWindup > 0)
    // ----------------------------------------------------------------

    public static void onWindup(int entityId, boolean isStart) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        Vec3 look = entity.getLookAngle();
        Vec3 back = look.scale(-1.0).normalize();

        double px = entity.getX();
        double py = entity.getY() + 0.9;
        double pz = entity.getZ();

        if (isStart) {
            // First frame: wide burst ring snapping into position
            spawnGatherRing(level, px, py, pz, back, 2.2, 20, 0.035);
            // A handful of loose poof wisps ejected outward so it "opens up"
            for (int i = 0; i < 6; i++) {
                double ox = (Math.random() - 0.5) * 2.5;
                double oy = (Math.random() - 0.5) * 1.0;
                double oz = (Math.random() - 0.5) * 2.5;
                level.addParticle(ParticleTypes.POOF,
                        px + ox, py + oy, pz + oz,
                        ox * 0.04, oy * 0.02, oz * 0.04);
            }
        } else {
            // Ongoing: tighter ring drifting straight toward player centre
            spawnGatherRing(level, px, py, pz, back, 1.4, 10, 0.025);
        }
    }

    /**
     * Spawns a ring of CLOUD particles in the plane perpendicular to {@code back},
     * offset one body-length behind the entity.  Each particle drifts inward.
     */
    private static void spawnGatherRing(ClientLevel level,
                                        double cx, double cy, double cz,
                                        Vec3 back,
                                        double radius, int count, double inwardSpeed) {
        Vec3 p1 = perpendicular(back);
        Vec3 p2 = back.cross(p1).normalize();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            // Radial direction in the ring plane
            double rx = p1.x * cos + p2.x * sin;
            double ry = p1.y * cos + p2.y * sin;
            double rz = p1.z * cos + p2.z * sin;

            // Spawn 1.5 blocks behind the player, spread to radius
            double sx = cx + back.x * 1.5 + rx * radius;
            double sy = cy + back.y * 1.5 + ry * radius;
            double sz = cz + back.z * 1.5 + rz * radius;

            // Drift inward (toward player) — cancel the radial component
            double vx = -rx * inwardSpeed;
            double vy = -ry * inwardSpeed;
            double vz = -rz * inwardSpeed;

            level.addParticle(ParticleTypes.CLOUD, sx, sy, sz, vx, vy, vz);
        }
    }

    // ----------------------------------------------------------------
    // DASH  (called every tick while boomDash > 0)
    // ----------------------------------------------------------------

    public static void onDash(int entityId, double dirX, double dirY, double dirZ, boolean isStart) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        Vec3 dir = new Vec3(dirX, dirY, dirZ).normalize();

        double px = entity.getX();
        double py = entity.getY() + 0.9;
        double pz = entity.getZ();

        if (isStart) {
            // Launch burst: a tight ring that explodes outward
            spawnWakeRing(level, px, py, pz, dir, 0.3, 20, 0.35);
            // A few enchanted-hit sparks at the leading edge for a "cutting" feel
            for (int i = 0; i < 8; i++) {
                double spread = 0.6;
                level.addParticle(ParticleTypes.ENCHANTED_HIT,
                        px + dir.x * 0.5 + (Math.random() - 0.5) * spread,
                        py + dir.y * 0.5 + (Math.random() - 0.5) * spread,
                        pz + dir.z * 0.5 + (Math.random() - 0.5) * spread,
                        dir.x * 0.06, dir.y * 0.06, dir.z * 0.06);
            }
        } else {
            // Ongoing: moderate expanding wake — the air tunnel
            spawnWakeRing(level, px, py, pz, dir, 0.35, 10, 0.18);
        }
    }

    /**
     * Spawns a ring of CLOUD particles centred slightly behind the entity,
     * perpendicular to travel direction.  Each particle drifts outward and
     * backwards to carve the tunnel silhouette.
     */
    private static void spawnWakeRing(ClientLevel level,
                                      double cx, double cy, double cz,
                                      Vec3 dir,
                                      double radius, int count, double outSpeed) {
        Vec3 back = dir.scale(-1.0);
        Vec3 p1 = perpendicular(dir);
        Vec3 p2 = dir.cross(p1).normalize();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            // Radial direction
            double rx = p1.x * cos + p2.x * sin;
            double ry = p1.y * cos + p2.y * sin;
            double rz = p1.z * cos + p2.z * sin;

            // Spawn at the player, tight around the body
            double sx = cx + rx * radius;
            double sy = cy + ry * radius;
            double sz = cz + rz * radius;

            // Expand outward + trail backward = open cone wake
            double vx = rx * outSpeed + back.x * 0.06;
            double vy = ry * outSpeed + back.y * 0.06;
            double vz = rz * outSpeed + back.z * 0.06;

            level.addParticle(ParticleTypes.CLOUD, sx, sy, sz, vx, vy, vz);
        }
    }

    // ----------------------------------------------------------------
    // IMPACT  (called once on terrain/entity collision)
    // ----------------------------------------------------------------

    public static void onImpact(double posX, double posY, double posZ) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        double cy = posY + 0.4;

        // Two concentric horizontal shockwave rings expanding outward
        spawnShockwaveRing(level, posX, cy, posZ, 0.8, 24);
        spawnShockwaveRing(level, posX, cy, posZ, 1.8, 16);

        // Central cloud burst upward and outward
        for (int i = 0; i < 18; i++) {
            double vx = (Math.random() - 0.5) * 0.45;
            double vy = Math.random() * 0.35 + 0.05;
            double vz = (Math.random() - 0.5) * 0.45;
            level.addParticle(ParticleTypes.CLOUD,
                    posX + (Math.random() - 0.5) * 1.8,
                    posY + Math.random() * 1.2,
                    posZ + (Math.random() - 0.5) * 1.8,
                    vx, vy, vz);
        }

        // Crisp enchanted-hit sparkle for the impact flash
        for (int i = 0; i < 10; i++) {
            double spread = 1.2;
            level.addParticle(ParticleTypes.ENCHANTED_HIT,
                    posX + (Math.random() - 0.5) * spread,
                    posY + Math.random() * spread,
                    posZ + (Math.random() - 0.5) * spread,
                    (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.5);
        }
    }

    /**
     * Flat horizontal ring of POOF particles expanding outward like a
     * ground-level sonic shockwave.
     */
    private static void spawnShockwaveRing(ClientLevel level,
                                           double cx, double cy, double cz,
                                           double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            level.addParticle(ParticleTypes.POOF,
                    cx + cos * radius, cy, cz + sin * radius,
                    cos * 0.22, 0.02, sin * 0.22);
        }
    }

    // ----------------------------------------------------------------
    // UTIL
    // ----------------------------------------------------------------

    /** Returns an arbitrary unit vector perpendicular to {@code v}. */
    private static Vec3 perpendicular(Vec3 v) {
        Vec3 arbitrary = Math.abs(v.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return v.cross(arbitrary).normalize();
    }
}