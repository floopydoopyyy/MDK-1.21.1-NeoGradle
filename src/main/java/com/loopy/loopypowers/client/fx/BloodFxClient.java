package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BloodFxClient {

    private static final Random RNG = new Random();
    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.05f), 1.25f);

    // Tracks active tethers (Caster ID -> Target ID)
    private static final Map<Integer, Integer> ACTIVE_BINDS = new HashMap<>();

    public static void updateBind(int casterId, int targetId, boolean active) {
        if (active) {
            ACTIVE_BINDS.put(casterId, targetId);
        } else {
            ACTIVE_BINDS.remove(casterId);
        }
    }

    // Ticked locally inside LoopypowersClient.java
    public static void tick(Minecraft client) {
        if (client.isPaused() || client.level == null) return;
        ClientLevel level = client.level;

        var it = ACTIVE_BINDS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity caster = level.getEntity(entry.getKey());
            Entity target = level.getEntity(entry.getValue());

            if (!(caster instanceof LivingEntity c) || !(target instanceof LivingEntity t) || !c.isAlive() || !t.isAlive()) {
                it.remove();
                continue;
            }

            double distSq = c.distanceToSqr(t);
            if (distSq > (20.0 * 20.0)) {
                it.remove(); // Failsafe if it broke out of range
                continue;
            }

            double dist = Math.sqrt(distSq);
            float strain = (float) Mth.clamp((dist - 10.0) / 10.0, 0.0, 1.0);

            if (c.tickCount % 2 == 0) {
                spawnBindAura(level, c, strain);
                spawnBindAura(level, t, strain);
                spawnTetherParticles(level, c, t);
            }
        }
    }

    public static void spawnWhip(int entityId, double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Entity e = client.level.getEntity(entityId);
        if (!(e instanceof LivingEntity player)) return;

        Vec3 start = player.getEyePosition();
        Vec3 end = new Vec3(x, y, z);
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3 dir = delta.scale(1.0 / len);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
        Vec3 perp = dir.cross(right).normalize();

        int steps = Mth.clamp((int) (len / 0.25), 6, 140);
        Vec3 p = start;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double wave = Math.sin(t * Math.PI);
            double spiralX = Math.cos(t * 12.0) * 0.5 * wave;
            double spiralY = Math.sin(t * 12.0) * 0.5 * wave;

            Vec3 offset = right.scale(spiralX).add(perp.scale(spiralY));

            client.level.addParticle(BLOOD_DUST, p.x + offset.x, p.y + offset.y, p.z + offset.z, 0, 0, 0);
            if (RNG.nextFloat() < 0.1f) {
                client.level.addParticle(ParticleTypes.DAMAGE_INDICATOR, p.x + offset.x, p.y + offset.y, p.z + offset.z, 0, 0, 0);
            }
            p = p.add(dir.scale(len / steps));
        }
    }

    private static void spawnTetherParticles(ClientLevel w, LivingEntity a, LivingEntity b) {
        Vec3 start = a.position().add(0, a.getBbHeight() * 0.6, 0);
        Vec3 end = b.position().add(0, b.getBbHeight() * 0.6, 0);
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3 dir = delta.scale(1.0 / len);
        int steps = Mth.clamp((int)(len / 0.8), 5, 40);

        Vec3 p = start;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double pulse = 0.08 + 0.06 * Math.sin((t * 8.0) + (w.getGameTime() * 0.35));
            double jitter = 0.06;

            double jx = (RNG.nextDouble() - 0.5) * jitter;
            double jy = (RNG.nextDouble() - 0.5) * jitter;
            double jz = (RNG.nextDouble() - 0.5) * jitter;

            // Notice we can bypass velocity by sending straight zeroes
            w.addParticle(BLOOD_DUST, p.x + jx, p.y + jy, p.z + jz, 0, 0, 0);
            p = p.add(dir.scale(len / steps));
        }

        if (RNG.nextFloat() < 0.85f) {
            w.addParticle(BLOOD_DUST, start.x, start.y, start.z, 0, 0, 0);
            w.addParticle(BLOOD_DUST, end.x, end.y, end.z, 0, 0, 0);
        }
    }

    private static void spawnBindAura(ClientLevel w, LivingEntity e, float intensity) {
        Vec3 p = e.position().add(0, e.getBbHeight() * 0.65, 0);
        int count = Mth.clamp((int)(1 + intensity * 4), 1, 6);

        for (int i = 0; i < count; i++) {
            double spreadX = (RNG.nextDouble() - 0.5) * (0.5 + intensity * 0.7);
            double spreadZ = (RNG.nextDouble() - 0.5) * (0.5 + intensity * 0.7);
            w.addParticle(BLOOD_DUST, p.x + spreadX, p.y, p.z + spreadZ, 0, 0, 0);
        }

        if (RNG.nextFloat() < (0.10f + intensity * 0.35f)) {
            w.addParticle(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 0, 0, 0);
        }
    }
}