package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public class FireFxClient {
    private static final Map<Integer, Integer> ACTIVE_ULTS = new HashMap<>();

    public static void updateUlt(int ownerId, boolean active) {
        if (active) {
            ACTIVE_ULTS.put(ownerId, 100); // 100 ticks = 5 seconds
        } else {
            ACTIVE_ULTS.remove(ownerId);
        }
    }

    public static void tick(Minecraft client) {
        if (client.isPaused() || client.level == null) return;
        ClientLevel level = client.level;

        var it = ACTIVE_ULTS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity owner = level.getEntity(entry.getKey());

            if (owner == null || !owner.isAlive()) {
                it.remove();
                continue;
            }

            int ticksRemaining = entry.getValue();
            if (ticksRemaining <= 0) {
                it.remove();
                continue;
            }

            float progress = 1f - (ticksRemaining / 100f);
            double intensity = 0.5 + progress * 2.5;

            int flameCount = (int)(6 * intensity);
            int lavaCount = (int)(1 + progress * 5);
            int smokeCount = (int)(3 * intensity);

            for(int i=0; i<flameCount; i++) {
                level.addParticle(ParticleTypes.FLAME,
                        owner.getX() + (level.random.nextDouble() - 0.5) * intensity,
                        owner.getY() + level.random.nextDouble() * 2 * intensity,
                        owner.getZ() + (level.random.nextDouble() - 0.5) * intensity,
                        0, 0.05, 0);
            }

            for(int i=0; i<lavaCount; i++) {
                level.addParticle(ParticleTypes.LAVA,
                        owner.getX() + (level.random.nextDouble() - 0.5),
                        owner.getY() + 0.5,
                        owner.getZ() + (level.random.nextDouble() - 0.5),
                        0, 0.1, 0);
            }

            for(int i=0; i<smokeCount; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        owner.getX() + (level.random.nextDouble() - 0.5) * 1.5,
                        owner.getY() + level.random.nextDouble() * 2,
                        owner.getZ() + (level.random.nextDouble() - 0.5) * 1.5,
                        0, 0.05, 0);
            }

            if (ticksRemaining < 20) {
                for (int i = 0; i < 15; i++) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            owner.getX() + (level.random.nextDouble() - 0.5) * 2.5,
                            owner.getY() + level.random.nextDouble() * 2,
                            owner.getZ() + (level.random.nextDouble() - 0.5) * 2.5,
                            0, 0.15, 0);
                }
            }

            entry.setValue(ticksRemaining - 1);
        }
    }
}