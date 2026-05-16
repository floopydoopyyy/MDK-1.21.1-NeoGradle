package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.entity.BlackHoleEntity;
import com.loopy.loopypowers.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleBlackHoleParticles(BlackHoleParticlePayload payload, IPayloadContext context) {
        // Enqueue work ensures this runs on the main game thread, preventing crashes
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;

            if (level == null) return;

            // Find the black hole on the client side using the ID we sent
            Entity entity = level.getEntity(payload.entityId());
            if (entity instanceof BlackHoleEntity bh) {
                // We will create this method inside BlackHoleEntity next!
                bh.spawnClientParticles(payload.lifeProgress());
            }
        });
    }

    public static void handleFateAura(FateAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.FateAuraClient.update(
                    payload.entityId(), payload.storedDamage(), payload.timerTicks()
            );
        });
    }

    public static void handleBloodWhip(BloodWhipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.BloodFxClient.spawnWhip(
                    payload.entityId(), payload.x(), payload.y(), payload.z()
            );
        });
    }

    public static void handleBloodBind(BloodBindPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.BloodFxClient.updateBind(
                    payload.casterId(), payload.targetId(), payload.active()
            );
        });
    }

    public static void handleBlackoutFx(BlackoutFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.DarknessFxClient.spawnBlackout(
                    payload.x(), payload.y(), payload.z()
            );
        });
    }

    public static void handleFractureFx(FractureFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.DimensionalFxClient.updateFracture(
                    payload.ownerId(), payload.x(), payload.y(), payload.z(), payload.seed(), payload.active()
            );
        });
    }

    public static void handleExplosionDropZone(com.loopy.loopypowers.network.payload.ExplosionDropZonePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.ExplosionFxClient.updateDropZone(
                    payload.ownerId(), payload.active(), payload.stage()
            );
        });
    }

    public static void handleFireUltFx(FireUltPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.loopy.loopypowers.client.fx.FireFxClient.updateUlt(
                    payload.ownerId(), payload.active()
            );
        });
    }
}