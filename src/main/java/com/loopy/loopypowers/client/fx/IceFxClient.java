package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Handles all client-side particle rendering for IcePower.
 * Mirrors the server-side constants exactly so the visuals are identical.
 * The shockwave ring particles are intentionally omitted here — they remain
 * server-side via sendParticles as agreed.
 */
public class IceFxClient {

    // ---- Dust colours (mirror server constants) ----
    private static final DustParticleOptions FRZ_BLUE_DUST    = new DustParticleOptions(new Vector3f(0.25f, 0.65f, 1.00f), 0.75f);
    private static final DustParticleOptions FRZ_SHIMMER_DUST = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 1.00f), 0.45f);
    private static final DustParticleOptions SPIKE_TRAIL_DUST = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1.00f), 1.10f);
    private static final DustParticleOptions SPIKE_PUFF_DUST  = new DustParticleOptions(new Vector3f(0.35f, 0.80f, 1.00f), 1.35f);
    private static final DustParticleOptions ULT_BLUE_DUST    = new DustParticleOptions(new Vector3f(0.20f, 0.55f, 1.00f), 0.90f);

    // ---- Blizzard constants (match server) ----
    private static final double ULT_BLIZZARD_RADIUS = 11.0;
    private static final int    ULT_SNOW_PER_TICK   = 50;
    private static final int    ULT_GUST_PER_TICK   = 30;

    // ---- Beam constants ----
    private static final int    BEAM_PARTICLE_DENSITY = 10;
    private static final double BEAM_SPIRAL_RADIUS    = 0.15;

    /* ================================================================
       FREEZE STAGE AURA  (IceFreezeStagePayload)
       Called every tick per frozen entity.
    ================================================================ */

    public static void onFreezeStage(int entityId, int stage, long gameTime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity ent = level.getEntity(entityId);
        if (!(ent instanceof LivingEntity e)) return;

        if (stage <= 0) return;
        // Low stages spawn at half-rate
        if (stage <= 2 && (gameTime & 1) == 1) return;

        Vec3 p  = e.position().add(0, e.getBbHeight() * 0.55, 0);
        double hw    = e.getBbWidth()  * 0.25;
        double hh    = e.getBbHeight() * 0.25;
        double speed = 0.035;

        int snow = switch (stage) { case 1 -> 1; case 2 -> 2; case 3 -> 5; case 4 -> 8; default -> 12; };
        level.addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, hw * (Math.random() * 2 - 1), hh * (Math.random() * 2 - 1), hw * (Math.random() * 2 - 1));
        for (int i = 1; i < snow; i++) spawnAt(level, ParticleTypes.SNOWFLAKE, p, hw, hh, speed);

        if (stage >= 2) for (int i = 0; i < stage; i++) spawnAt(level, ParticleTypes.WHITE_ASH, p, hw, hh, speed);
        if (stage >= 3 && (gameTime % 3) == 0) spawnAt(level, ParticleTypes.ELECTRIC_SPARK, p, hw, hh, speed);
        if (stage >= 4) {
            spawnAt(level, FRZ_BLUE_DUST,    p, hw, hh, speed);
            spawnAt(level, FRZ_BLUE_DUST,    p, hw, hh, speed);
            spawnAt(level, FRZ_BLUE_DUST,    p, hw, hh, speed);
        }
        if (stage >= 5) onShatterReady(level, e, p, hw, hh, speed, gameTime);
    }

    /** Rotating halo ring + sparkle for stage 5 "shatter ready" state. */
    private static void onShatterReady(ClientLevel level, LivingEntity e, Vec3 p,
                                        double hw, double hh, double speed, long gameTime) {
        // Dense aura
        for (int i = 0; i < 5; i++) spawnAt(level, FRZ_BLUE_DUST,    p, hw, hh, speed * 1.5);
        for (int i = 0; i < 4; i++) spawnAt(level, FRZ_SHIMMER_DUST, p, hw, hh, speed * 1.5);

        if ((gameTime % 4) == 0) {
            spawnAt(level, ParticleTypes.END_ROD, p, hw, hh, speed * 2.0);
            for (int i = 0; i < 4; i++) {
                Vec3 ep = p.add(0, -0.2, 0);
                level.addParticle(ParticleTypes.ENCHANT,
                        ep.x + (Math.random() - 0.5) * hw * 2,
                        ep.y + (Math.random() - 0.5) * hh * 2,
                        ep.z + (Math.random() - 0.5) * hw * 2,
                        0, 0, 0);
            }
        }

        // Rotating halo ring — only every other tick (matches server guard)
        if ((gameTime & 1) == 1) return;
        Vec3 c = e.position().add(0, e.getBbHeight() * 0.72, 0);
        double baseAng = gameTime * 0.35;
        double r = 0.55 + 0.06 * Math.sin(gameTime * 0.45);
        int points = 14;
        for (int i = 0; i < points; i++) {
            double a = baseAng + (i * (Math.PI * 2.0 / points));
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + Math.sin(a * 2.0) * 0.05;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            if ((i % 2) == 0) level.addParticle(FRZ_SHIMMER_DUST,          x, y, z, 0.02, 0.02, 0.02);
            if ((i % 3) == 0) level.addParticle(FRZ_BLUE_DUST,             x, y, z, 0.02, 0.02, 0.02);
            if ((i % 4) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.02, 0.02, 0.02);
        }
        if ((gameTime % 6) == 0) {
            for (int i = 0; i < 6;  i++) spawnAt(level, ParticleTypes.ENCHANT,   c.add(0, -0.15, 0), 0.25, 0.22, 0);
            for (int i = 0; i < 10; i++) spawnAt(level, ParticleTypes.SNOWFLAKE, c.add(0, -0.15, 0), 0.28, 0.22, 0);
        }
    }

    /* ================================================================
       SHATTER BURST  (IceShatterPayload)
       One-shot burst when an entity shatters.
    ================================================================ */

    public static void onShatter(int entityId, double fx, double fy, double fz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        // Prefer live entity position; fall back to transmitted coords
        double x = fx, y = fy, z = fz;
        Entity ent = level.getEntity(entityId);
        if (ent instanceof LivingEntity le) {
            Vec3 p = le.position().add(0, le.getBbHeight() * 0.55, 0);
            x = p.x; y = p.y; z = p.z;
        }

        final double px = x, py = y, pz = z;
        for (int i = 0; i < 60; i++) spawnAt(level, ParticleTypes.SNOWFLAKE,      new Vec3(px, py, pz), 0.35, 0.35, 0);
        for (int i = 0; i < 18; i++) spawnAt(level, ParticleTypes.ELECTRIC_SPARK, new Vec3(px, py, pz), 0.25, 0.20, 0);
        for (int i = 0; i < 24; i++) spawnAt(level, FRZ_BLUE_DUST,                new Vec3(px, py, pz), 0.25, 0.20, 0);
        for (int i = 0; i < 16; i++) spawnAt(level, FRZ_SHIMMER_DUST,             new Vec3(px, py, pz), 0.22, 0.18, 0);
    }

    /* ================================================================
       SPIKE GROUND TRAIL  (IceSpikeTrailPayload)
    ================================================================ */

    public static void onSpikeTrail(double startX, double startZ,
                                     double targetX, double targetZ,
                                     float progress, int seed, int yHint) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double back = 0.18;
        double fx  = Mth.lerp(progress, startX, targetX);
        double fz  = Mth.lerp(progress, startZ, targetZ);
        double px0 = Mth.lerp(Mth.clamp(progress - back, 0.0f, 1.0f), startX, targetX);
        double pz0 = Mth.lerp(Mth.clamp(progress - back, 0.0f, 1.0f), startZ, targetZ);

        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double a = i / (double) steps;
            double x = Mth.lerp(a, px0, fx);
            double z = Mth.lerp(a, pz0, fz);
            int y = findSurfaceY(level, Mth.floor(x), Mth.floor(z), yHint);

            double jx = ((seed * 31L + i * 17L) % 100) / 100.0 - 0.5;
            double jz = ((seed * 13L + i * 29L) % 100) / 100.0 - 0.5;

            level.addParticle(SPIKE_TRAIL_DUST, x + (jx * 0.08), y + 0.06, z + (jz * 0.08), 0, 0, 0);
            if ((i & 1) == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.08, z, 0.05, 0.01, 0.05);
        }
    }

    /* ================================================================
       SPIKE TIP PUFF  (IceSpikePuffPayload)
    ================================================================ */

    public static void onSpikePuff(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        for (int i = 0; i < 18; i++) spawnAt(level, SPIKE_PUFF_DUST,         new Vec3(x, y + 0.35, z), 0.22, 0.25, 0.02);
        for (int i = 0; i < 10; i++) spawnAt(level, ParticleTypes.SNOWFLAKE,  new Vec3(x, y + 0.45, z), 0.25, 0.20, 0.0);
    }

    /* ================================================================
       BEAM CHARGE RING  (IceBeamChargePayload)
    ================================================================ */

    public static void onBeamCharge(int entityId, int tickCount) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity ent = level.getEntity(entityId);
        if (!(ent instanceof LivingEntity player)) return;

        Vec3 dir    = player.getLookAngle().normalize();
        Vec3 muzzle = getBeamMuzzlePos(player, dir);
        int  points = 14;
        double r    = 0.22;

        for (int i = 0; i < points; i++) {
            double a = (tickCount * 0.45) + (i * (Math.PI * 2.0 / points));
            double x = muzzle.x + Math.cos(a) * r;
            double z = muzzle.z + Math.sin(a) * r;
            double y = muzzle.y + (Math.random() - 0.5) * 0.12;

            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            if ((i % 3) == 0) level.addParticle(ParticleTypes.ENCHANT,   x, y, z, 0.02, 0.02, 0.02);
            if ((i &  1) == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.02, 0.02, 0.02);
        }
    }

    /* ================================================================
       BEAM FIRE SPIRAL  (IceBeamFirePayload)
    ================================================================ */

    public static void onBeamFire(double startX, double startY, double startZ,
                                   double endX,   double endY,   double endZ,
                                   long gameTime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 start = new Vec3(startX, startY, startZ);
        Vec3 end   = new Vec3(endX,   endY,   endZ);
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.001) return;

        Vec3 dir   = delta.scale(1.0 / len);
        Vec3 right = new Vec3(0, 1, 0).cross(dir);
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up2 = dir.cross(right).normalize();

        int steps = Mth.clamp((int) (len * BEAM_PARTICLE_DENSITY), 24, 140);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = start.add(delta.scale(t));

            level.addParticle(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 0, 0, 0);

            double ang   = (gameTime * 0.45) + (i * 0.65);
            Vec3   swirl = p.add(right.scale(Math.cos(ang) * BEAM_SPIRAL_RADIUS))
                            .add(up2.scale(Math.sin(ang) * BEAM_SPIRAL_RADIUS));
            level.addParticle(ParticleTypes.SNOWFLAKE, swirl.x, swirl.y, swirl.z, 0, 0, 0);

            if ((i % 10) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0.03, 0.03, 0.03);
            if ((i % 14) == 0) level.addParticle(ParticleTypes.END_ROD,        p.x, p.y, p.z, 0, 0, 0);
        }
    }

    /* ================================================================
       BLIZZARD OVERHEAD + SNOW AROUND ENTITIES  (IceBlizzardPayload)
    ================================================================ */

    public static void onBlizzard(int entityId, int ultTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity ent = level.getEntity(entityId);
        if (ent == null) return;

        Vec3 c = ent.position();

        // Use ultTick as a seed so each tick is differently seeded,
        // but deterministic enough that server/client agree on coverage.
        java.util.Random rng = new java.util.Random(ultTick * 0x9e3779b97f4a7c15L);

        // Overhead snowflakes
        for (int i = 0; i < ULT_SNOW_PER_TICK; i++) {
            double r = Math.sqrt(rng.nextDouble()) * ULT_BLIZZARD_RADIUS;
            double a = rng.nextDouble() * Math.PI * 2.0;
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 6.0 + rng.nextDouble() * 3.0;
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.25, 0.15, 0.25);
            if ((i % 12) == 0) level.addParticle(ParticleTypes.END_ROD, x, y - 0.4, z, 0, 0, 0);
        }

        // Ground-level gusts
        for (int i = 0; i < ULT_GUST_PER_TICK; i++) {
            double r = Math.sqrt(rng.nextDouble()) * (ULT_BLIZZARD_RADIUS * 0.9);
            double a = rng.nextDouble() * Math.PI * 2.0;
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 1.0 + rng.nextDouble() * 2.0;
            level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.22, 0.18, 0.22);
            if ((i & 3) == 0) level.addParticle(ULT_BLUE_DUST, x, y, z, 0.10, 0.08, 0.10);
        }

        // Snow swirls around nearby entities
        AABB area = ent.getBoundingBox().inflate(ULT_BLIZZARD_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, en -> en.isAlive() && en != ent)) {
            Vec3 ep = e.position();
            for (int i = 0; i < 3; i++) {
                double ox = (rng.nextDouble() - 0.5) * 1.5;
                double oz = (rng.nextDouble() - 0.5) * 1.5;
                double oy = rng.nextDouble() * e.getBbHeight();
                level.addParticle(ParticleTypes.SNOWFLAKE, ep.x + ox, ep.y + oy, ep.z + oz, 0.5, 0.1, 0.5);
                if (rng.nextBoolean()) level.addParticle(ParticleTypes.WHITE_ASH, ep.x + ox, ep.y + oy, ep.z + oz, 0.2, 0.0, 0.2);
            }
        }
    }

    /* ================================================================
       HELPERS
    ================================================================ */

    /** Lightweight client-side surface Y finder — no side effects. */
    private static int findSurfaceY(ClientLevel level, int bx, int bz, int yHint) {
        int startY = Mth.clamp(yHint + 3, level.getMinBuildHeight() + 1, level.getHeight() - 1);
        for (int y = startY; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState here  = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());

            boolean hereOk = (here.isAir() || here.getCollisionShape(level, pos).isEmpty())
                    && here.getFluidState().isEmpty();
            boolean belowSolid = below.isFaceSturdy(level, pos.below(), Direction.UP)
                    || (!below.getFluidState().isEmpty());

            if (hereOk && belowSolid) return y;
        }
        return yHint;
    }

    /** Computes the beam muzzle position from any LivingEntity on the client. */
    private static Vec3 getBeamMuzzlePos(LivingEntity entity, Vec3 dir) {
        Vec3 right = new Vec3(0, 1, 0).cross(dir);
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();

        // Use main arm side; default right if we can't determine
        double side = 0.24;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            side = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) ? 0.24 : -0.24;
        }

        return entity.getEyePosition().add(dir.scale(0.30)).add(0.0, -0.28, 0.0).add(right.scale(side));
    }

    /** Scatters a particle uniformly within a spread box centred on p. */
    private static void spawnAt(ClientLevel level, net.minecraft.core.particles.ParticleOptions type,
                                  Vec3 p, double hw, double hh, double speed) {
        level.addParticle(type,
                p.x + (Math.random() * 2 - 1) * hw,
                p.y + (Math.random() * 2 - 1) * hh,
                p.z + (Math.random() * 2 - 1) * hw,
                (Math.random() * 2 - 1) * speed,
                (Math.random() * 2 - 1) * speed,
                (Math.random() * 2 - 1) * speed);
    }
}
