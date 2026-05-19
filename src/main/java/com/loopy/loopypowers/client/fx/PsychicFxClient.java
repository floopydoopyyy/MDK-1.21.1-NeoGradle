package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side particle rendering for PuppetryEntity and CompelEntity projectiles.
 * All methods are called from ClientPayloadHandler on the main thread.
 */
public class PsychicFxClient {

    /* ============================================================
       Shared particle colours
       ============================================================ */

    private static final DustParticleOptions MAIN_PINK =
            new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.6f), 1.1f);

    private static final DustParticleOptions ACCENT_LIGHT =
            new DustParticleOptions(new Vector3f(1.0f, 0.6f, 0.9f), 0.8f);

    private static final DustParticleOptions ACCENT_DARK =
            new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.6f), 0.9f);

    private static final DustParticleOptions DEEP_PURPLE =
            new DustParticleOptions(new Vector3f(0.4f, 0.0f, 0.8f), 1.3f);

    private static final DustParticleOptions MID_PURPLE =
            new DustParticleOptions(new Vector3f(0.7f, 0.1f, 0.9f), 1.0f);

    private static final DustParticleOptions BRIGHT_PINK =
            new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.9f), 0.7f);

    private static final DustParticleOptions SPIKE_IMPACT_DARK =
            new DustParticleOptions(new Vector3f(0.50f, 0.00f, 0.60f), 1.0f);

    private static final DustParticleOptions SPIKE_IMPACT_LIGHT =
            new DustParticleOptions(new Vector3f(0.90f, 0.20f, 0.60f), 0.8f);

    private static final DustParticleOptions LEECH_END =
            new DustParticleOptions(new Vector3f(1.00f, 0.20f, 0.80f), 1.0f);

    /* ============================================================
       AURAS (Ticked constantly by Payloads)
       ============================================================ */

    public static void onCompelAura(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        // Dynamically scale to the entity's size so it surrounds them
        double hw = entity.getBbWidth() * 1.3;
        double hh = entity.getBbHeight();

        for (int i = 0; i < 4; i++) {
            level.addParticle(MAIN_PINK,
                    entity.getX() + (Math.random() - 0.5) * hw,
                    entity.getY() + Math.random() * hh,
                    entity.getZ() + (Math.random() - 0.5) * hw,
                    (Math.random() - 0.5) * 0.02, 0.03, (Math.random() - 0.5) * 0.02);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ACCENT_LIGHT,
                    entity.getX() + (Math.random() - 0.5) * hw * 1.2,
                    entity.getY() + Math.random() * hh * 1.2,
                    entity.getZ() + (Math.random() - 0.5) * hw * 1.2,
                    (Math.random() - 0.5) * 0.02, 0.04, (Math.random() - 0.5) * 0.02);
        }
    }

    public static void onSpikeAura ( int entityId){
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            Entity entity = level.getEntity(entityId);
            if (entity == null) return;

            // around head
            double hw = entity.getBbWidth() * 1.5;
            double headY = entity.getY() + (entity.getBbHeight() * 0.85);

            if (Math.random() < 0.7) {
                level.addParticle(MAIN_PINK,
                        entity.getX() + (Math.random() - 0.5) * hw,
                        headY + (Math.random() - 0.5) * 0.5,
                        entity.getZ() + (Math.random() - 0.5) * hw,
                        (Math.random() - 0.5) * 0.01, 0.01, (Math.random() - 0.5) * 0.01);
            }

            if (Math.random() < 0.4) {
                level.addParticle(ACCENT_DARK,
                        entity.getX() + (Math.random() - 0.5) * hw,
                        headY + (Math.random() - 0.5) * 0.4,
                        entity.getZ() + (Math.random() - 0.5) * hw,
                        (Math.random() - 0.5) * 0.01, 0.005, (Math.random() - 0.5) * 0.01);
            }
        }

    public static void onPossessedAura(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        // Ultimate aura - huge, dominating, dark and bright mixed
        double hw = entity.getBbWidth() * 1.5;
        double hh = entity.getBbHeight() * 1.2;

        for (int i = 0; i < 6; i++) {
            level.addParticle(MAIN_PINK,
                    entity.getX() + (Math.random() - 0.5) * hw,
                    entity.getY() + Math.random() * hh,
                    entity.getZ() + (Math.random() - 0.5) * hw,
                    (Math.random() - 0.5) * 0.05, 0.05, (Math.random() - 0.5) * 0.05);
        }
        for (int i = 0; i < 4; i++) {
            level.addParticle(ACCENT_LIGHT,
                    entity.getX() + (Math.random() - 0.5) * hw,
                    entity.getY() + Math.random() * hh,
                    entity.getZ() + (Math.random() - 0.5) * hw,
                    (Math.random() - 0.5) * 0.03, 0.04, (Math.random() - 0.5) * 0.03);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(DEEP_PURPLE,
                    entity.getX() + (Math.random() - 0.5) * hw * 1.3,
                    entity.getY() + Math.random() * hh,
                    entity.getZ() + (Math.random() - 0.5) * hw * 1.3,
                    (Math.random() - 0.5) * 0.04, 0.02, (Math.random() - 0.5) * 0.04);
        }
    }

    /* ============================================================
       PuppetryEntity — tick trail
       ============================================================ */

    /**
     * Called every tick while the Puppetry projectile is in flight.
     *
     * Five passes (up from the original four, with higher segment counts):
     * 1. Outer double helix       – 5 segments, DEEP_PURPLE / MID_PURPLE
     * 2. Counter-spinning inner   – 3 segments, BRIGHT_PINK
     * 3. Core line                – 6 segments, MAIN_PINK
     * 4. Slow outer aura ring     – 4 segments, MID_PURPLE  (new, larger radius)
     * 5. Leading tip burst        – DEEP_PURPLE + ACCENT_LIGHT + BRIGHT_PINK
     */
    public static void onPuppetryTick(
            double x, double y, double z,
            double vx, double vy, double vz,
            int life) {

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 pos = new Vec3(x, y, z);
        Vec3 vel = new Vec3(vx, vy, vz);
        if (vel.lengthSqr() < 0.001) return;

        // Use the same horizontal-perp frame as the original server code
        Vec3 dir  = vel.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        Vec3 up = dir.cross(side).normalize();

        // ── 1. Outer double helix — 5 segments (original: 3) ────────
        float pupSpeed  = 1.9f;
        float pupRadius = 0.25f;
        float pupLength = 0.25f;

        for (int i = 0; i < 5; i++) {
            float base = (life * pupSpeed) + (i * (float) (Math.PI * 2.0 / 5.0));
            double back = -i * pupLength;

            // Strand A — deep purple
            Vec3 posA = pos
                    .add(dir.scale(back))
                    .add(side.scale(Math.cos(base) * pupRadius))
                    .add(up.scale(Math.sin(base) * pupRadius));
            level.addParticle(DEEP_PURPLE, posA.x, posA.y, posA.z, 0, 0, 0);

            // Strand B — mid purple (opposite)
            double baseB = base + Math.PI;
            Vec3 posB = pos
                    .add(dir.scale(back))
                    .add(side.scale(Math.cos(baseB) * pupRadius))
                    .add(up.scale(Math.sin(baseB) * pupRadius));
            level.addParticle(MID_PURPLE, posB.x, posB.y, posB.z, 0.02, 0.02, 0.02);
        }

        // ── 2. Counter-spinning inner core — 3 segments (original: 2) ──
        float innerRadius = 0.08f;
        for (int i = 0; i < 3; i++) {
            float base = -(life * pupSpeed * 1.5f) + (i * (float) (Math.PI * 2.0 / 3.0));
            double back = -i * pupLength * 0.8;

            Vec3 corePos = pos
                    .add(dir.scale(back))
                    .add(side.scale(Math.cos(base) * innerRadius))
                    .add(up.scale(Math.sin(base) * innerRadius));
            level.addParticle(BRIGHT_PINK, corePos.x, corePos.y, corePos.z, 0, 0, 0);
        }

        // ── 3. Core line — 6 segments (original: 4) ─────────────────
        for (int i = 0; i < 6; i++) {
            Vec3 linePos = pos.subtract(dir.scale(i * 0.22));
            level.addParticle(MAIN_PINK, linePos.x, linePos.y, linePos.z, 0, 0, 0);
        }

        // ── 4. Slow outer aura ring — 4 segments, larger radius (new) ─
        float outerRadius = 0.45f;
        float outerSpeed  = 0.55f;
        for (int i = 0; i < 4; i++) {
            float base = -(life * outerSpeed) + (i * (float) (Math.PI / 2.0));
            Vec3 outerPos = pos
                    .add(side.scale(Math.cos(base) * outerRadius))
                    .add(up.scale(Math.sin(base) * outerRadius));
            level.addParticle(MID_PURPLE, outerPos.x, outerPos.y, outerPos.z, 0, 0, 0);
        }

        // ── 5. Leading tip — triple burst (original: double) ─────────
        Vec3 tip = pos.add(dir.scale(0.35));
        level.addParticle(DEEP_PURPLE,  tip.x, tip.y, tip.z, 0.05, 0.05, 0.05);
        level.addParticle(ACCENT_LIGHT, tip.x, tip.y, tip.z, 0.05, 0.05, 0.05);
        level.addParticle(BRIGHT_PINK,  tip.x, tip.y, tip.z, 0.04, 0.04, 0.04);
    }

    /* ============================================================
       PuppetryEntity — hit burst
       ============================================================ */

    /**
     * Called once when the Puppetry projectile strikes a target.
     * Spiral burst around target, dense impact cloud, and FX ring at projectile position.
     */
    public static void onPuppetryHit(
            int targetEntityId,
            double projX, double projY, double projZ,
            int life) {

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity rawTarget = level.getEntity(targetEntityId);
        if (!(rawTarget instanceof LivingEntity target)) return;

        float radius = 0.25f;
        float pspeed = 0.6f;
        double midY  = target.getY() + target.getBbHeight() * 0.5;

        // ── Spiral burst around target ────────────────────────────────
        for (int i = 0; i < 6; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 3);

            level.addParticle(MAIN_PINK,
                    target.getX() + Math.cos(angle) * radius,
                    midY,
                    target.getZ() + Math.sin(angle) * radius,
                    0, 0, 0);
            level.addParticle(ACCENT_LIGHT,
                    target.getX() + Math.cos(angle + 0.5) * (radius * 0.6),
                    midY + 0.1,
                    target.getZ() + Math.sin(angle + 0.5) * (radius * 0.6),
                    0, 0, 0);
        }

        // ── Impact burst cloud ────────────────────────────────────────
        for (int i = 0; i < 15; i++) {
            level.addParticle(ACCENT_DARK,
                    target.getX(), midY, target.getZ(),
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.4,
                    (Math.random() - 0.5) * 0.3);
        }
        // Extra deep-purple cloud for extra grandeur
        for (int i = 0; i < 12; i++) {
            level.addParticle(DEEP_PURPLE,
                    target.getX(), midY, target.getZ(),
                    (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.5);
        }

        // ── FX ring at projectile position ────────────────────────────
        for (int i = 0; i < 4; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 2);

            level.addParticle(MAIN_PINK,
                    projX + Math.cos(angle) * radius,
                    projY + 0.1,
                    projZ + Math.sin(angle) * radius,
                    0, 0, 0);
            level.addParticle(ACCENT_LIGHT,
                    projX + Math.cos(angle + 0.6) * (radius * 0.7),
                    projY + 0.12,
                    projZ + Math.sin(angle + 0.6) * (radius * 0.7),
                    0, 0, 0);
        }
    }

    /* ============================================================
       CompelEntity — tick trail
       ============================================================ */

    /**
     * Called every tick while the Compel projectile is in flight.
     * Restores the original single-helix (3 segments × 2 colours) + core-line passes.
     */
    public static void onCompelTick(
            double x, double y, double z,
            double vx, double vy, double vz,
            int life) {

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 pos = new Vec3(x, y, z);
        Vec3 vel = new Vec3(vx, vy, vz);
        if (vel.lengthSqr() < 0.001) return;

        Vec3 dir  = vel.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        Vec3 up = new Vec3(0, 1, 0);

        float compRadius = 0.12f;
        float compLength = 0.4f;
        float compSpeed  = 0.6f;

        // ── Spiral helix — 3 segments, 2 colours each ────────────────
        for (int i = 0; i < 3; i++) {
            float angle = (life * compSpeed) + (i * 2.0f);

            Vec3 segPos = pos
                    .add(dir.scale(-i * compLength))
                    .add(side.scale(Math.cos(angle) * compRadius))
                    .add(up.scale(Math.sin(angle) * compRadius));

            level.addParticle(MAIN_PINK,    segPos.x, segPos.y, segPos.z, 0,    0,    0);
            level.addParticle(ACCENT_LIGHT, segPos.x, segPos.y, segPos.z, 0.01, 0.01, 0.01);
        }

        // ── Core line — 3 segments ────────────────────────────────────
        for (int i = 0; i < 3; i++) {
            Vec3 linePos = pos.subtract(dir.scale(i * 0.25));
            level.addParticle(MAIN_PINK, linePos.x, linePos.y, linePos.z, 0, 0, 0);
        }
    }

    /* ============================================================
       CompelEntity — hit burst
       ============================================================ */

    /**
     * Called once when the Compel projectile strikes a target.
     * Spiral burst around target and central impact cloud.
     */
    public static void onCompelHit(int targetEntityId, int life) {

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity rawTarget = level.getEntity(targetEntityId);
        if (!(rawTarget instanceof LivingEntity target)) return;

        float radius = 0.25f;
        float pspeed = 0.6f;
        double midY  = target.getY(0.5);

        // ── Spiral burst around target ────────────────────────────────
        for (int i = 0; i < 6; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 3);

            level.addParticle(MAIN_PINK,
                    target.getX() + Math.cos(angle) * radius,
                    midY,
                    target.getZ() + Math.sin(angle) * radius,
                    0, 0, 0);
            level.addParticle(ACCENT_LIGHT,
                    target.getX() + Math.cos(angle + 0.5) * (radius * 0.6),
                    midY + 0.1,
                    target.getZ() + Math.sin(angle + 0.5) * (radius * 0.6),
                    0, 0, 0);
        }

        // ── Impact burst ──────────────────────────────────────────────
        for (int i = 0; i < 15; i++) {
            level.addParticle(ACCENT_DARK,
                    target.getX(), midY, target.getZ(),
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.4,
                    (Math.random() - 0.5) * 0.3);
        }
    }

    /* ============================================================
       One-shot utility effects
       ============================================================ */

    public static void onSpikeImpact(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        double y = entity.getY(0.5);
        for (int i = 0; i < 20; i++) {
            level.addParticle(SPIKE_IMPACT_DARK,
                    entity.getX() + (Math.random() - 0.5) * 0.8,
                    y + (Math.random() - 0.5) * 0.8,
                    entity.getZ() + (Math.random() - 0.5) * 0.8,
                    0, 0, 0);
        }
        for (int i = 0; i < 12; i++) {
            level.addParticle(SPIKE_IMPACT_LIGHT,
                    entity.getX() + (Math.random() - 0.5) * 0.6,
                    y + (Math.random() - 0.5) * 0.6,
                    entity.getZ() + (Math.random() - 0.5) * 0.6,
                    0, 0, 0);
        }
    }

    public static void onSpikeBeam(Vec3 from, Vec3 to, boolean isChain) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        int    segments = isChain ? 10 : 18;
        double offset   = isChain ? 0.385 : 0.55;

        Vec3 step    = to.subtract(from).scale(1.0 / segments);
        Vec3 beamDir = to.subtract(from).normalize();
        Vec3 perp    = Math.abs(beamDir.y) < 0.9
                ? new Vec3(-beamDir.z, 0, beamDir.x).normalize()
                : new Vec3(1, 0, 0);
        Vec3 perp2   = beamDir.cross(perp).normalize();

        Vec3 current = from;
        java.util.Random rng = new java.util.Random(from.hashCode());

        for (int i = 0; i < segments; i++) {
            Vec3 next = current.add(step);

            double jag  = (i % 2 == 0 ? 1 : -1) * offset * (0.5 + rng.nextDouble() * 0.5);
            double jag2 = (i % 3 == 0 ? 1 : -1) * offset * 0.4 * rng.nextDouble();
            Vec3 jaggedNext = next.add(perp.scale(jag)).add(perp2.scale(jag2));

            for (int j = 0; j <= 4; j++) {
                Vec3 p = current.lerp(jaggedNext, j / 4.0);
                level.addParticle(MAIN_PINK,    p.x, p.y, p.z, 0, 0, 0);
                level.addParticle(ACCENT_LIGHT, p.x, p.y, p.z, 0, 0, 0);
            }
            current = jaggedNext;
        }
    }

    public static void onLeech(int targetId, int casterId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity target = level.getEntity(targetId);
        Entity caster = level.getEntity(casterId);
        if (target == null || caster == null) return;

        Vec3 from    = new Vec3(target.getX(), target.getY(0.7), target.getZ());
        Vec3 to      = new Vec3(caster.getX(), caster.getY(0.7), caster.getZ());
        Vec3 step    = to.subtract(from).scale(1.0 / 10);
        Vec3 current = from;

        for (int i = 0; i < 10; i++) {
            double curve = Math.sin(i / 10.0 * Math.PI) * 0.15;
            level.addParticle(MAIN_PINK,    current.x, current.y - curve, current.z, 0, 0, 0);
            level.addParticle(ACCENT_LIGHT, current.x, current.y - curve, current.z, 0, 0, 0);
            current = current.add(step);
        }

        for (int i = 0; i < 10; i++) {
            level.addParticle(LEECH_END,
                    caster.getX() + (Math.random() - 0.5) * 0.6,
                    caster.getY(0.6) + (Math.random() - 0.5) * 0.6,
                    caster.getZ() + (Math.random() - 0.5) * 0.6,
                    0, 0, 0);
        }
    }
}