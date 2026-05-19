package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.network.payload.TeleportParticlePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public class TeleportFxClient {

    private static final DustParticleOptions FRENZY_DUST = new DustParticleOptions(new Vector3f(0.55f, 0.0f, 0.85f), 1.5f);
    private static final DustParticleOptions FRENZY_RING = new DustParticleOptions(new Vector3f(0.85f, 0.2f, 1.0f), 1.2f);

    public static void onParticleEvent(TeleportParticlePayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        double x1 = p.x1(), y1 = p.y1(), z1 = p.z1();
        double x2 = p.x2(), y2 = p.y2(), z2 = p.z2();

        switch (p.eventId()) {
            case TeleportParticlePayload.DODGE -> {
                spawnParticles(level, ParticleTypes.PORTAL, x1, y1 + 1.0, z1, 40, 0.4, 0.8, 0.4, 0.08);
            }
            case TeleportParticlePayload.BLINK -> {
                spawnBlinkTrail(level, x1, y1, z1, x2, y2, z2);
                spawnParticles(level, ParticleTypes.PORTAL, x1, y1 + 1.0, z1, 30, 0.4, 0.7, 0.4, 0.1);
                spawnParticles(level, ParticleTypes.PORTAL, x2, y2 + 1.0, z2, 30, 0.4, 0.7, 0.4, 0.1);
            }
            case TeleportParticlePayload.SWAP_WHIFF -> {
                spawnAimBeam(level, x1, y1, z1, x2, y2, z2);
                spawnParticles(level, ParticleTypes.PORTAL, x2, y2, z2, 18, 0.25, 0.25, 0.25, 0.06);
            }
            case TeleportParticlePayload.SWAP_HIT -> {
                // Approximate eye/mid heights based on entity positions passed in
                spawnAimBeam(level, x1, y1 + 1.6, z1, x2, y2 + 1.0, z2);
                spawnParticles(level, ParticleTypes.PORTAL, x2, y2 + 1.0, z2, 18, 0.25, 0.25, 0.25, 0.06);

                spawnBlinkTrail(level, x1, y1, z1, x2, y2, z2);
                spawnBlinkTrail(level, x2, y2, z2, x1, y1, z1);

                spawnParticles(level, ParticleTypes.PORTAL, x1, y1 + 1.0, z1, 50, 0.5, 0.8, 0.5, 0.1);
                spawnParticles(level, ParticleTypes.PORTAL, x2, y2 + 1.0, z2, 50, 0.5, 0.8, 0.5, 0.1);
            }
            case TeleportParticlePayload.FRENZY_TICK -> {
                // Ring
                int points = 80;
                double radius = 11.0;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double px = x1 + Math.cos(angle) * radius;
                    double pz = z1 + Math.sin(angle) * radius;
                    level.addParticle(FRENZY_RING, px, y1 + 0.1, pz, 0.02, 0.02, 0.02);
                }
                // Aura
                spawnParticles(level, FRENZY_DUST, x1, y1 + 1.0, z1, 3, 0.6, 1.0, 0.6, 0.02);
                spawnParticles(level, ParticleTypes.REVERSE_PORTAL, x1, y1 + 0.5, z1, 20, 0.3, 0.6, 0.3, 0.01);
            }
            case TeleportParticlePayload.FRENZY_STRIKE -> {
                spawnParticles(level, FRENZY_DUST, x1, y1 + 1.0, z1, 20, 0.3, 0.6, 0.3, 0.08);
            }
        }
    }

    private static void spawnAimBeam(ClientLevel level, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001) return;

        int steps = Mth.clamp((int) (len * 18), 10, 140);
        double sx = dx / steps, sy = dy / steps, sz = dz / steps;
        double px = x1, py = y1, pz = z1;

        for (int i = 0; i <= steps; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0.02, 0.02, 0.02);
            px += sx; py += sy; pz += sz;
        }
    }

    private static void spawnBlinkTrail(ClientLevel level, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001) return;

        int steps = Mth.clamp((int) (len * 12), 8, 80);
        double sx = dx / steps, sy = dy / steps, sz = dz / steps;
        double px = x1, py = y1, pz = z1;

        for (int i = 0; i <= steps; i++) {
            spawnParticles(level, ParticleTypes.PORTAL, px, py + 1.0, pz, 1, 0.02, 0.15, 0.02, 0.0);
            px += sx; py += sy; pz += sz;
        }
    }

    private static void spawnParticles(ClientLevel level, ParticleOptions type, double x, double y, double z, int count, double xd, double yd, double zd, double speed) {
        RandomSource r = level.random;
        if (count == 0) {
            level.addParticle(type, x, y, z, xd, yd, zd);
            return;
        }
        for (int i = 0; i < count; i++) {
            level.addParticle(type,
                    x + r.nextGaussian() * xd, y + r.nextGaussian() * yd, z + r.nextGaussian() * zd,
                    r.nextGaussian() * speed, r.nextGaussian() * speed, r.nextGaussian() * speed);
        }
    }
}