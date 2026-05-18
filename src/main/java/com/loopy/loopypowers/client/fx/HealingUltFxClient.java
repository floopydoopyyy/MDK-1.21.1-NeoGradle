package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/**
 * Client-side FX for the healing ult
 *
 * Five distinct visual identities — one per HP phase — plus a burst
 * effect on every phase transition so the shift is unmistakeable.
 *
 *  Phase 5  OVERFLOW    — Double helix + rotating crown + golden stars
 *  Phase 4  EXCEPTIONAL — Orbiting ring + END_ROD scatter + rising wisps
 *  Phase 3  EQUILIBRIUM — Steady orbit + calm firework wisps
 *  Phase 2  INADEQUATE  — Urgent pulsing clusters + enchanted sparks
 *  Phase 1  EXPOSED     —  burst, sparks, ground stuff
 */
public class HealingUltFxClient {

    // Matches the server-side HEAL_DUST (pinkish-red, bio-energy)
    private static final DustParticleOptions HEAL_DUST =
            new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.4f), 1.2f);

    // Brighter/larger variant used for bursts and high-energy phases
    private static final DustParticleOptions HEAL_DUST_BRIGHT =
            new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.65f), 1.8f);

    // ----------------------------------------------------------------
    // PUBLIC ENTRY POINT
    // ----------------------------------------------------------------

    public static void onUltTick(int entityId, int phase, int ultTicks, boolean isPhaseChange) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        double cx = entity.getX();
        double cy = entity.getY() + 0.9; // mid-torso
        double cz = entity.getZ();

        // Angle seed advances every tick — drives all rotating patterns
        double angle = ultTicks * 0.22;

        // One-time burst on phase transition
        if (isPhaseChange) {
            spawnPhaseChangeBurst(level, cx, cy, cz, phase);
        }

        // Continuous per-tick FX
        switch (phase) {
            case 5 -> tickOverflow(level, cx, cy, cz, angle, ultTicks);
            case 4 -> tickExceptional(level, cx, cy, cz, angle, ultTicks);
            case 3 -> tickEquilibrium(level, cx, cy, cz, angle, ultTicks);
            case 2 -> tickInadequate(level, cx, cy, cz, angle, ultTicks);
            case 1 -> tickExposed(level, cx, cy, cz, angle, ultTicks);
        }
    }

    // ----------------------------------------------------------------
    // PER-PHASE TICK FX
    // ----------------------------------------------------------------

    /**
     * Phase 5 — OVERFLOW (HP ≥ 100%)
     * Double HEAL_DUST helix spiraling around the body, a slow END_ROD
     * crown halo overhead, and periodic TOTEM stars bursting upward.
     */
    private static void tickOverflow(ClientLevel level, double cx, double cy, double cz,
                                     double angle, int ticks) {
        // Double helix — two arms, each oscillating in height
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + arm * Math.PI;
            double heightOff = Math.sin(a * 1.6) * 0.55;
            double sx = cx + Math.cos(a) * 1.0;
            double sy = cy + heightOff;
            double sz = cz + Math.sin(a) * 1.0;
            level.addParticle(HEAL_DUST_BRIGHT, sx, sy, sz, 0, 0.04, 0);
        }

        // Slow counter-rotating END_ROD crown halo
        for (int i = 0; i < 3; i++) {
            double a = -angle * 0.55 + (2 * Math.PI / 3.0) * i;
            double sx = cx + Math.cos(a) * 0.65;
            double sz = cz + Math.sin(a) * 0.65;
            level.addParticle(ParticleTypes.END_ROD, sx, cy + 1.25, sz, 0, 0.025, 0);
        }

        // Periodic TOTEM stars floating upward
        if (ticks % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                        cx + (Math.random() - 0.5) * 0.5,
                        cy + (Math.random() - 0.5) * 0.5,
                        cz + (Math.random() - 0.5) * 0.5,
                        (Math.random() - 0.5) * 0.22,
                        Math.random() * 0.18 + 0.04,
                        (Math.random() - 0.5) * 0.22);
            }
        }
    }

    /**
     * Phase 4 — EXCEPTIONAL (HP ≥ 80%)
     * 5-point rotating HEAL_DUST orbit, scattered END_ROD sparkles
     * catching the light, and gentle rising FIREWORK wisps.
     */
    private static void tickExceptional(ClientLevel level, double cx, double cy, double cz,
                                        double angle, int ticks) {
        // 5-point orbit ring of HEAL_DUST
        spawnOrbitRing(level, cx, cy, cz, 0.85, 5, angle, HEAL_DUST_BRIGHT, 0.012);

        // END_ROD sparkles floating upward and outward
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.END_ROD,
                    cx + (Math.random() - 0.5) * 0.7,
                    cy + (Math.random() - 0.5) * 0.6,
                    cz + (Math.random() - 0.5) * 0.7,
                    (Math.random() - 0.5) * 0.14,
                    Math.random() * 0.12 + 0.02,
                    (Math.random() - 0.5) * 0.14);
        }

        // Lazy FIREWORK wisps rising from the feet every 4 ticks
        if (ticks % 4 == 0) {
            level.addParticle(ParticleTypes.FIREWORK,
                    cx + (Math.random() - 0.5) * 0.4,
                    cy - 0.3,
                    cz + (Math.random() - 0.5) * 0.4,
                    0, 0.055, 0);
        }
    }

    /**
     * Phase 3 — EQUILIBRIUM (HP ≥ 60%)
     * Steady 4-point orbit ring — visually calm and balanced.
     * Occasional FIREWORK wisps and a rare END_ROD to keep it alive.
     */
    private static void tickEquilibrium(ClientLevel level, double cx, double cy, double cz,
                                        double angle, int ticks) {
        // Slower, steady orbit to sell the "balanced" feel
        spawnOrbitRing(level, cx, cy, cz, 0.72, 4, angle * 0.8, HEAL_DUST, 0.008);

        // Light FIREWORK wisps
        if (ticks % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.FIREWORK,
                        cx + (Math.random() - 0.5) * 0.55,
                        cy + (Math.random() - 0.5) * 0.55,
                        cz + (Math.random() - 0.5) * 0.55,
                        (Math.random() - 0.5) * 0.04, 0.03, (Math.random() - 0.5) * 0.04);
            }
        }

        // Infrequent END_ROD so it doesn't look flat
        if (ticks % 5 == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    cx + (Math.random() - 0.5) * 0.4,
                    cy + 0.35,
                    cz + (Math.random() - 0.5) * 0.4,
                    0, 0.035, 0);
        }
    }

    /**
     * Phase 2 — INADEQUATE (HP ≥ 40%)
     * Urgent pulsing HEAL_DUST clusters whose speed fluctuates with a
     * sine wave, plus frequent ENCHANTED_HIT sparks for a "sparking
     * wounds" feel.
     */
    private static void tickInadequate(ClientLevel level, double cx, double cy, double cz,
                                       double angle, int ticks) {
        // Pulse speed oscillates — the pulsing is visible
        double pulse = 0.03 + Math.abs(Math.sin(ticks * 0.28)) * 0.07;

        for (int i = 0; i < 4; i++) {
            level.addParticle(HEAL_DUST,
                    cx + (Math.random() - 0.5) * 0.45,
                    cy + (Math.random() - 0.5) * 0.55,
                    cz + (Math.random() - 0.5) * 0.45,
                    (Math.random() - 0.5) * pulse * 3.5,
                    (Math.random() * 0.5 - 0.1) * pulse * 2.5,
                    (Math.random() - 0.5) * pulse * 3.5);
        }

        // ENCHANTED_HIT sparks — urgency indicator
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ENCHANTED_HIT,
                    cx + (Math.random() - 0.5) * 0.6,
                    cy + (Math.random() - 0.5) * 0.65,
                    cz + (Math.random() - 0.5) * 0.6,
                    (Math.random() - 0.5) * 0.14,
                    (Math.random() - 0.5) * 0.14,
                    (Math.random() - 0.5) * 0.14);
        }

        if (ticks % 3 == 0) {
            level.addParticle(ParticleTypes.FIREWORK,
                    cx + (Math.random() - 0.5) * 0.45,
                    cy + (Math.random() - 0.5) * 0.45,
                    cz + (Math.random() - 0.5) * 0.45,
                    (Math.random() - 0.5) * 0.08, 0.04, (Math.random() - 0.5) * 0.08);
        }
    }

    /**
     * Phase 1 — EXPOSED (HP &lt; 40%)
     * Chaotic, frantic HEAL_DUST bursting in all directions, dense
     * ENCHANTED_HIT sparks, rapid END_ROD flashes, and ground-level
     * POOF puffs — the power is fighting to keep the player alive.
     */
    private static void tickExposed(ClientLevel level, double cx, double cy, double cz,
                                    double angle, int ticks) {
        // Chaotic HEAL_DUST — wide, fast, erratic
        for (int i = 0; i < 6; i++) {
            level.addParticle(HEAL_DUST_BRIGHT,
                    cx + (Math.random() - 0.5) * 0.6,
                    cy + (Math.random() - 0.5) * 0.75,
                    cz + (Math.random() - 0.5) * 0.6,
                    (Math.random() - 0.5) * 0.22,
                    (Math.random() - 0.3) * 0.18,
                    (Math.random() - 0.5) * 0.22);
        }

        // Dense ENCHANTED_HIT — "sparking under pressure"
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.ENCHANTED_HIT,
                    cx + (Math.random() - 0.5) * 0.75,
                    cy + (Math.random() - 0.5) * 0.85,
                    cz + (Math.random() - 0.5) * 0.75,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2);
        }

        // Rapid END_ROD flashes every other tick
        if (ticks % 2 == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    cx + (Math.random() - 0.5) * 0.35,
                    cy + (Math.random() - 0.5) * 0.45,
                    cz + (Math.random() - 0.5) * 0.35,
                    (Math.random() - 0.5) * 0.14,
                    Math.random() * 0.1,
                    (Math.random() - 0.5) * 0.14);
        }

        // Ground POOF puffs — like the floor is trembling
        if (ticks % 4 == 0) {
            level.addParticle(ParticleTypes.POOF,
                    cx + (Math.random() - 0.5) * 0.4,
                    cy - 0.75,
                    cz + (Math.random() - 0.5) * 0.4,
                    0, 0.03, 0);
        }
    }

    // ----------------------------------------------------------------
    // PHASE-CHANGE BURSTS  (one-shot, fires on the first tick of a new phase)
    // ----------------------------------------------------------------

    private static void spawnPhaseChangeBurst(ClientLevel level, double cx, double cy, double cz, int phase) {
        switch (phase) {
            case 5 -> { // OVERFLOW — radiant celebration
                spawnRadialBurst(level, cx, cy, cz, 30, 0.32, HEAL_DUST_BRIGHT);
                spawnRadialBurst(level, cx, cy, cz, 14, 0.28, ParticleTypes.TOTEM_OF_UNDYING);
                spawnHorizontalRing(level, cx, cy - 0.3, cz, 2.0, 20, ParticleTypes.END_ROD, 0.18);
                for (int i = 0; i < 5; i++) {
                    level.addParticle(ParticleTypes.FLASH,
                            cx + (Math.random() - 0.5) * 1.4,
                            cy + (Math.random() - 0.5) * 1.0,
                            cz + (Math.random() - 0.5) * 1.4,
                            0, 0, 0);
                }
            }
            case 4 -> { // EXCEPTIONAL — strong outward burst
                spawnRadialBurst(level, cx, cy, cz, 22, 0.28, HEAL_DUST_BRIGHT);
                spawnRadialBurst(level, cx, cy, cz, 16, 0.24, ParticleTypes.END_ROD);
                spawnHorizontalRing(level, cx, cy - 0.3, cz, 1.5, 16, HEAL_DUST, 0.14);
            }
            case 3 -> { // EQUILIBRIUM — smooth stabilising ring
                spawnHorizontalRing(level, cx, cy, cz, 1.2, 14, HEAL_DUST, 0.09);
                spawnRadialBurst(level, cx, cy, cz, 10, 0.18, ParticleTypes.FIREWORK);
            }
            case 2 -> { // INADEQUATE — urgent spark ring
                spawnHorizontalRing(level, cx, cy, cz, 1.0, 12, ParticleTypes.ENCHANTED_HIT, 0.11);
                spawnRadialBurst(level, cx, cy, cz, 16, 0.2, HEAL_DUST);
            }
            case 1 -> { // EXPOSED — alarm burst, maximum intensity
                spawnRadialBurst(level, cx, cy, cz, 26, 0.3, HEAL_DUST_BRIGHT);
                spawnRadialBurst(level, cx, cy, cz, 12, 0.25, ParticleTypes.ENCHANTED_HIT);
                spawnHorizontalRing(level, cx, cy - 0.3, cz, 1.8, 18, HEAL_DUST, 0.2);
                for (int i = 0; i < 8; i++) {
                    level.addParticle(ParticleTypes.FLASH,
                            cx + (Math.random() - 0.5) * 1.6,
                            cy + Math.random() * 1.0,
                            cz + (Math.random() - 0.5) * 1.6,
                            0, 0, 0);
                }
                // Big shockwave gust at the feet
                level.addParticle(ParticleTypes.GUST_EMITTER_LARGE, cx, cy - 0.6, cz, 0, 0, 0);
            }
        }
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------

    /** N particles evenly spaced in a horizontal orbit ring at height cy. */
    private static void spawnOrbitRing(ClientLevel level,
                                       double cx, double cy, double cz,
                                       double radius, int count, double angleOffset,
                                       ParticleOptions particle, double vy) {
        for (int i = 0; i < count; i++) {
            double a = angleOffset + (2 * Math.PI / count) * i;
            level.addParticle(particle,
                    cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius,
                    0, vy, 0);
        }
    }

    /** Flat horizontal ring of particles expanding outward from cy. */
    private static void spawnHorizontalRing(ClientLevel level,
                                            double cx, double cy, double cz,
                                            double radius, int count,
                                            ParticleOptions particle, double outSpeed) {
        for (int i = 0; i < count; i++) {
            double a = (2 * Math.PI / count) * i;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            level.addParticle(particle,
                    cx + cos * radius, cy, cz + sin * radius,
                    cos * outSpeed, 0.02, sin * outSpeed);
        }
    }

    /** Omnidirectional spherical burst — all particles originate from centre. */
    private static void spawnRadialBurst(ClientLevel level,
                                         double cx, double cy, double cz,
                                         int count, double speed,
                                         ParticleOptions particle) {
        for (int i = 0; i < count; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi   = Math.acos(2 * Math.random() - 1);
            double vx = Math.sin(phi) * Math.cos(theta) * speed;
            double vy = Math.cos(phi) * speed * 0.65; // slightly compressed vertically
            double vz = Math.sin(phi) * Math.sin(theta) * speed;
            level.addParticle(particle, cx, cy, cz, vx, vy, vz);
        }
    }
}