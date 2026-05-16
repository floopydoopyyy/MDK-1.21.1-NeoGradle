package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.power.ExplosionPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class ExplosionFxClient {
    private static final Map<Integer, Integer> ACTIVE_DROP_ZONES = new HashMap<>();

    public static void updateDropZone(int ownerId, boolean active, int stage) {
        if (active) {
            ACTIVE_DROP_ZONES.put(ownerId, stage);
        } else {
            ACTIVE_DROP_ZONES.remove(ownerId);
        }
    }

    public static void tick(Minecraft client) {
        if (client.isPaused() || client.level == null) return;
        ClientLevel level = client.level;

        var it = ACTIVE_DROP_ZONES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity owner = level.getEntity(entry.getKey());

            // Safely remove the drop zone if the player logs out or dies
            if (owner == null || !owner.isAlive()) {
                it.remove();
                continue;
            }

            int ultStage = entry.getValue();

            Vec3 start = owner.position();
            Vec3 end = start.subtract(0, 100, 0);

            HitResult hit = level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner
            ));

            if (hit.getType() == HitResult.Type.MISS) continue;

            Vec3 groundPos = hit.getLocation();

            double radius = (ultStage >= ExplosionPower.ULT_POPS_ACTUAL) ?
                    ExplosionPower.ULT_FINAL_DMG_RADIUS : ExplosionPower.ULT_POP_DMG_RADIUS;

            int points = 48;
            double time = (level.getGameTime() % 20) / 20.0;
            double offsetAngle = time * Math.PI * 2;

            for (int i = 0; i < points; i++) {
                double angle = offsetAngle + (2 * Math.PI * i) / points;
                double x = groundPos.x + Math.cos(angle) * radius;
                double z = groundPos.z + Math.sin(angle) * radius;

                level.addParticle(ParticleTypes.FLAME, x, groundPos.y + 0.1, z, 0, 0, 0);
            }
        }
    }
}