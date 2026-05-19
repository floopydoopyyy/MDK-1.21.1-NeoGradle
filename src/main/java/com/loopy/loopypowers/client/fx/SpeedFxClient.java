package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side particle rendering for SpeedPower.
 * All methods are called from ClientPayloadHandler on the main thread.
 */
public class SpeedFxClient {

    /* ============================================================
       Particle colours
       ============================================================ */

    private static final DustParticleOptions WHITE_BRIGHT =
            new DustParticleOptions(new Vector3f(1.00f, 1.00f, 1.00f), 1.3f);

    private static final DustParticleOptions WHITE_PALE =
            new DustParticleOptions(new Vector3f(0.85f, 0.85f, 0.85f), 1.0f);

    private static final DustParticleOptions WHITE_STREAK =
            new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.95f), 0.8f);

    /* ============================================================
       Pinball — anchor boundary ring
       ============================================================ */

    /**
     * Renders the 36-point boundary ring around the pinball anchor.
     * Called every time a SpeedPinballAnchorPayload is received
     * (the server already gates this to every 3 ticks).
     */
    public static void onPinballAnchor(double anchorX, double anchorY, double anchorZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double radius = 10.0; // matches PINBALL_RADIUS
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double px = anchorX + Math.cos(angle) * radius;
            double pz = anchorZ + Math.sin(angle) * radius;

            level.addParticle(WHITE_PALE,
                    px, anchorY + 0.1, pz,
                    0, 0.02, 0);

            if (i % 4 == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        px, anchorY + 0.2, pz,
                        0, 0.05, 0);
            }
        }
    }

    /* ============================================================
       Dash — cast burst and tick trail
       ============================================================ */

    /**
     * Cast burst for primary dash.
     */
    public static void onDashCast(double x, double y, double z,
                                  double lookX, double lookY, double lookZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        for (int i = 0; i < 20; i++) {
            level.addParticle(WHITE_PALE,
                    x, y, z,
                    (Math.random() - 0.5) * 0.8,
                    Math.random() * 0.5,
                    (Math.random() - 0.5) * 0.8);
        }

        // Backward arc showing push-off
        for (int i = 0; i < 10; i++) {
            level.addParticle(WHITE_BRIGHT,
                    x, y, z,
                    -lookX * 0.5 + (Math.random() - 0.5) * 0.2,
                    -lookY * 0.5 + Math.random() * 0.2,
                    -lookZ * 0.5 + (Math.random() - 0.5) * 0.2);
        }
    }

    /**
     * Trailing streaks while actively dashing.
     */
    public static void onDashTrail(double x, double y, double z,
                                   double vx, double vy, double vz,
                                   long gameTime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 vel = new Vec3(vx, vy, vz);
        if (vel.lengthSqr() < 0.001) return;
        Vec3 back = vel.normalize().scale(-1.0);

        double py = y + 1.0;

        // Thin central streak
        for (int i = 0; i < 2; i++) {
            level.addParticle(WHITE_STREAK,
                    x + (Math.random() - 0.5) * 0.3,
                    py + (Math.random() - 0.5) * 0.3,
                    z + (Math.random() - 0.5) * 0.3,
                    0, 0, 0);
        }

        // Sparks
        if (Math.random() < 0.3) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x + (Math.random() - 0.5) * 0.5,
                    py + (Math.random() - 0.5) * 0.5,
                    z + (Math.random() - 0.5) * 0.5,
                    back.x * 0.2, back.y * 0.2, back.z * 0.2);
        }

        // Periodic sweep attack particle to show breaking the sound barrier
        if (gameTime % 2 == 0) {
            level.addParticle(ParticleTypes.SWEEP_ATTACK, x, py, z, 0, 0, 0);
        }
    }

    /* ============================================================
       Overdrive (Ultimate) — cast burst and tick trail
       ============================================================ */

    /**
     * Massive radial burst when Overdrive is activated.
     */
    public static void onOverdriveCast(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double oy = y + 1.0;

        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy, z, 1.5, 0.5, 1.5);
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy + 1.0, z, 1.5, 0.5, 1.5);

        for (int i = 0; i < 80; i++) {
            level.addParticle(WHITE_PALE,
                    x, oy, z,
                    (Math.random() - 0.5) * 1.8,
                    Math.random() * 1.5,
                    (Math.random() - 0.5) * 1.8);
        }

        for (int i = 0; i < 30; i++) {
            level.addParticle(WHITE_BRIGHT,
                    x, oy, z,
                    (Math.random() - 0.5) * 1.5,
                    Math.random() * 1.0,
                    (Math.random() - 0.5) * 1.5);
        }

        for (int i = 0; i < 40; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x, oy, z,
                    (Math.random() - 0.5) * 2.5,
                    Math.random() * 2.0,
                    (Math.random() - 0.5) * 2.5);
        }

        // High-speed "speed lines" shooting out
        for (int i = 0; i < 24; i++) {
            level.addParticle(ParticleTypes.END_ROD,
                    x, oy, z,
                    (Math.random() - 0.5) * 2.5,
                    (Math.random() - 0.5) * 2.5,
                    (Math.random() - 0.5) * 2.5);
        }

        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.FLASH, x, oy + Math.random(), z, 0, 0, 0);
        }
    }

    /**
     * Thick slipstream trail while Overdrive is active.
     */
    public static void onOverdriveTrail(double x, double y, double z,
                                        double vx, double vy, double vz,
                                        long gameTime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 pos  = new Vec3(x, y + 1.0, z); // 1.0 above feet
        Vec3 vel  = new Vec3(vx, vy, vz);
        if (vel.lengthSqr() < 0.001) return;

        Vec3 back = vel.normalize().scale(-1.0);
        Vec3 arbitrary = Math.abs(back.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = back.cross(arbitrary).normalize();
        Vec3 up    = right.cross(back).normalize();

        // 1. Far behind: thick pale streak dust
        Vec3 far = pos.add(back.scale(1.2));
        for (int i = 0; i < 8; i++) {
            level.addParticle(WHITE_STREAK,
                    far.x + (Math.random() - 0.5) * 0.8,
                    far.y + (Math.random() - 0.5) * 0.8,
                    far.z + (Math.random() - 0.5) * 0.8,
                    0, 0, 0);
        }

        // 2. Closer behind: bright dust core
        Vec3 close = pos.add(back.scale(0.5));
        for (int i = 0; i < 6; i++) {
            level.addParticle(WHITE_BRIGHT,
                    close.x + (Math.random() - 0.5) * 0.5,
                    close.y + (Math.random() - 0.5) * 0.5,
                    close.z + (Math.random() - 0.5) * 0.5,
                    0, 0, 0);
        }

        // 3. Shines/sparks scattered heavily along the slipstream
        for (int i = 0; i < 8; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + (Math.random() - 0.5) * 1.5,
                    pos.y + (Math.random() - 0.5) * 1.5,
                    pos.z + (Math.random() - 0.5) * 1.5,
                    back.x * 0.4, back.y * 0.4, back.z * 0.4);
        }

        // Speed lines flying backwards
        if (Math.random() < 0.4) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.x + (Math.random() - 0.5) * 1.2,
                    pos.y + (Math.random() - 0.5) * 1.2,
                    pos.z + (Math.random() - 0.5) * 1.2,
                    back.x * 0.8, back.y * 0.8, back.z * 0.8);
        }

        // 4. Perpendicular shockwave rings (every 4 ticks)
        if (gameTime % 4 == 0) {
            int points = 32;
            double ringRadius = 1.2;

            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                Vec3 p = pos.add(right.scale(Math.cos(angle) * ringRadius))
                        .add(up.scale(Math.sin(angle) * ringRadius));

                level.addParticle(WHITE_PALE, p.x, p.y, p.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
            }

            // Scatter sparks off the ring
            for (int i = 0; i < 10; i++) {
                double angle = Math.random() * Math.PI * 2.0;
                Vec3 p = pos.add(right.scale(Math.cos(angle) * (ringRadius + 0.3)))
                        .add(up.scale(Math.sin(angle) * (ringRadius + 0.3)));
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, back.x * 0.2, back.y * 0.2, back.z * 0.2);
            }
        }
    }

    /* ============================================================
       Low Health passive burst
       ============================================================ */

    /**
     * Sent once from onDamaged() -> tryLowHealthBurst().
     */
    public static void onLowHealthBurst(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double oy = y + 0.5;
        level.addParticle(WHITE_BRIGHT,             x, oy, z, 0.3, 0.4, 0.3);
        level.addParticle(ParticleTypes.SWEEP_ATTACK, x, oy, z, 0.4, 0.2, 0.4);
    }

    /* ============================================================
       Overdrive — collision explosions
       ============================================================ */

    /**
     * EXPLOSION_EMITTER burst at the player's position.
     *
     * heavy=true  — entity collision (4 particles, wide spread)
     * heavy=false — block collision  (3 particles, tight spread)
     */
    public static void onExplosionFx(double x, double y, double z, boolean heavy) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double oy = y + 1.0;
        if (heavy) {
            // initial
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy, z, 0.6, 0.2, 0.6);
            }
            // extra
            level.addParticle(ParticleTypes.FLASH, x, oy, z, 0, 0, 0);
            for (int i = 0; i < 15; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, oy, z, (Math.random() - 0.5) * 1.5, Math.random() * 1.0, (Math.random() - 0.5) * 1.5);
            }
        } else {
            // big impact
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy, z, 0.15, 0.10, 0.15);
            }
            // Less dense sparks for walls
            for (int i = 0; i < 8; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, oy, z, (Math.random() - 0.5) * 0.8, Math.random() * 0.5, (Math.random() - 0.5) * 0.8);
            }
        }
    }
}