package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class FateAuraClient {

    private static final Map<Integer, ClientFate> ACTIVE_FATES = new HashMap<>();
    private static final int FATE_TIMER_MAX = 300;

    private static class ClientFate {
        float storedDamage;
        int timerTicks;
    }

    // Called by the payload handler to start/update/stop the effect
    public static void update(int entityId, float storedDamage, int timerTicks) {
        if (timerTicks <= 0) {
            ACTIVE_FATES.remove(entityId); // Stop rendering if timer is 0
        } else {
            ClientFate fate = ACTIVE_FATES.computeIfAbsent(entityId, k -> new ClientFate());
            fate.storedDamage = storedDamage;
            fate.timerTicks = timerTicks;
        }
    }

    // Ticked locally every frame!
    public static void tick(Minecraft client) {
        if (client.isPaused() || client.level == null) return;
        ClientLevel level = client.level;

        var it = ACTIVE_FATES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            int entityId = entry.getKey();
            ClientFate fate = entry.getValue();

            fate.timerTicks--;
            if (fate.timerTicks <= 0) {
                it.remove();
                continue;
            }

            Entity entity = level.getEntity(entityId);
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }

            spawnClientAura(level, entity, fate);
        }
    }

    private static void spawnClientAura(ClientLevel level, Entity entity, ClientFate fate) {
        float timerFraction = (float) fate.timerTicks / FATE_TIMER_MAX;
        long time = level.getGameTime();
        double orbitRadius = 0.8 + 0.35;

        // Trail
        double speed = 0.05 + (1.0 - timerFraction) * 0.35;
        double angle = time * speed;
        int trailPoints = (int)(2 + speed * 5);

        for (int t = 0; t < trailPoints; t++) {
            double trailAngle = angle - (t * 0.15);
            double cx = entity.getX() + Math.cos(trailAngle) * orbitRadius;
            double cz = entity.getZ() + Math.sin(trailAngle) * orbitRadius;
            double cy = entity.getY(0.55);
            float size = 1.2f - (t * 0.08f);

            level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.5f, 0.1f), Math.max(0.4f, size)),
                    cx, cy, cz, 0, 0, 0);
        }

        // Flames
        if (timerFraction < 0.3f && entity.tickCount % 2 == 0) {
            int flameCount = (int)((0.3f - timerFraction) * 10);
            for (int i = 0; i < flameCount; i++) {
                double randAngle = level.random.nextDouble() * Math.PI * 2;
                double r = orbitRadius + 0.1 + level.random.nextDouble() * 0.2;
                double fx = entity.getX() + Math.cos(randAngle) * r;
                double fz = entity.getZ() + Math.sin(randAngle) * r;
                double fy = entity.getY(0.55) + level.random.nextDouble() * 0.3;
                level.addParticle(ParticleTypes.FLAME, fx, fy, fz, 0, 0.01, 0);
            }
        }

        // Stars
        if (entity.tickCount % 3 == 0) {
            int starCount = Mth.clamp((int)fate.storedDamage / 2, 0, 10);
            for (int i = 0; i < starCount; i++) {
                long seed = entity.getId() * 341873128712L + i * 132897987541L;
                double offsetX = ((seed >> 16) & 0xFF) / 255.0 * 2.0 - 1.0;
                double offsetY = ((seed >> 8)  & 0xFF) / 255.0;
                double offsetZ = ((seed)       & 0xFF) / 255.0 * 2.0 - 1.0;
                level.addParticle(ParticleTypes.WHITE_ASH,
                        entity.getX() + offsetX * 0.8,
                        entity.getY(offsetY * 1.2),
                        entity.getZ() + offsetZ * 0.8,
                        0, 0, 0);
            }
        }
    }
}