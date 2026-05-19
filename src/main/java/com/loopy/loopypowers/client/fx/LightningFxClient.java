package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class LightningFxClient {

    public static final DustParticleOptions BOLT_YELLOW = new DustParticleOptions(new Vector3f(1.00f, 0.88f, 0.05f), 1.4f);
    public static final DustParticleOptions BOLT_GOLD   = new DustParticleOptions(new Vector3f(1.00f, 0.95f, 0.55f), 1.1f);
    public static final DustParticleOptions BOLT_WHITE  = new DustParticleOptions(new Vector3f(1.00f, 1.00f, 0.82f), 1.6f);

    private static final double CLAP_RANGE     = 7.0;
    private static final double CLAP_ANGLE_DEG = 65.0;

    public static void onClap(int entityId, boolean isParty) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        Vec3 center = entity.position().add(0, 0.8, 0);
        Vec3 forward = entity.getViewVector(1.0f).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
        double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);

        int depthSlices = 6;
        int arcPoints = 20;

        for (int d = 1; d <= depthSlices; d++) {
            double depth = CLAP_RANGE * ((double) d / depthSlices);
            double arcWidth = depth * Math.tan(halfAngle);
            Vec3 slicePos = center.add(forward.scale(depth));

            for (int i = 0; i <= arcPoints; i++) {
                double lateral = -arcWidth + 2.0 * arcWidth * ((double) i / arcPoints);
                double jitter = (Math.random() - 0.5) * 0.6;

                double x = slicePos.x + right.x * (lateral + jitter);
                double z = slicePos.z + right.z * (lateral + jitter);
                double y = slicePos.y + (Math.random() - 0.5) * 1.5;

                if (isParty) {
                    if (Math.random() > 0.50) continue;
                    Vector3f rainbow = new Vector3f((float)Math.random(), (float)Math.random(), (float)Math.random());
                    level.addParticle(new DustParticleOptions(rainbow, 1.1f), x, y, z, 0, 0, 0);
                } else {
                    if (Math.random() > 0.65) continue;
                    DustParticleOptions col = d <= 2 ? BOLT_WHITE : d <= 4 ? BOLT_YELLOW : BOLT_GOLD;
                    level.addParticle(col, x, y, z, 0, 0, 0);

                    if (Math.random() < 0.20) {
                        level.addParticle(ParticleTypes.END_ROD, x, y, z, forward.x * 0.12, 0.01, forward.z * 0.12);
                    }
                }
            }
        }

        if (!isParty) {
            for(int i = 0; i < 18; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, (Math.random()-0.5)*0.6, (Math.random()-0.5)*0.6, (Math.random()-0.5)*0.6);
            for(int i = 0; i < 6; i++)  level.addParticle(BOLT_WHITE, center.x, center.y, center.z, (Math.random()-0.5)*0.3, (Math.random()-0.5)*0.3, (Math.random()-0.5)*0.3);
        }
    }

    public static void onClapHit(int targetId, float t, boolean isParty) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity target = level.getEntity(targetId);
        if (!(target instanceof LivingEntity le)) return;

        if (isParty) {
            int count = (int)(25 + 30 * t);
            for (int i = 0; i < count; i++) {
                Vector3f color = new Vector3f((float)Math.random(), (float)Math.random(), (float)Math.random());
                level.addParticle(new DustParticleOptions(color, 0.85f), target.getX(), target.getY() + 1.0, target.getZ(), (Math.random()-0.5)*0.6, Math.random()*0.4, (Math.random()-0.5)*0.6);
            }
        } else {
            for(int i = 0; i < (int)(8 + 20 * t); i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), (Math.random()-0.5)*0.8, Math.random()*0.6, (Math.random()-0.5)*0.8);
            for(int i = 0; i < (int)(6 + 12 * t); i++) level.addParticle(BOLT_YELLOW, target.getX(), target.getY() + 1.0, target.getZ(), (Math.random()-0.5)*0.6, Math.random()*0.4, (Math.random()-0.5)*0.6);
        }
    }

    public static void onSuperchargeBurst(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        Vec3 center = entity.position();

        // Charge Ring
        int ringPoints = 40;
        for (int i = 0; i < ringPoints; i++) {
            double angle = 2 * Math.PI * i / ringPoints;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x + Math.cos(angle) * 1.2, center.y + 0.2, center.z + Math.sin(angle) * 1.2, 0, 0, 0);
        }

        // Expanding Outer Ring
        int outerPoints = 32;
        double outerSpeed  = 0.30;
        for (int i = 0; i < outerPoints; i++) {
            double angle = i * Math.PI * 2.0 / outerPoints;
            DustParticleOptions col = (i % 3 == 0) ? BOLT_WHITE : (i % 3 == 1) ? BOLT_YELLOW : BOLT_GOLD;
            level.addParticle(col, center.x + Math.cos(angle) * 0.4, center.y + 0.3, center.z + Math.sin(angle) * 0.4, Math.cos(angle) * outerSpeed, 0.01, Math.sin(angle) * outerSpeed);
        }

        // Dense internal burst
        for(int i = 0; i < 80; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.5, center.z, (Math.random()-0.5)*1.4, Math.random()*1.2, (Math.random()-0.5)*1.4);
        for(int i = 0; i < 40; i++) level.addParticle(BOLT_YELLOW, center.x, center.y + 1.0, center.z, (Math.random()-0.5)*1.0, Math.random()*1.0, (Math.random()-0.5)*1.0);
        for(int i = 0; i < 16; i++) level.addParticle(BOLT_WHITE, center.x, center.y + 1.0, center.z, (Math.random()-0.5)*0.6, Math.random()*0.8, (Math.random()-0.5)*0.6);

        // Player FX
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 2.0 / 8;
            level.addParticle(ParticleTypes.END_ROD, center.x + Math.cos(angle) * 0.4, center.y + 0.8 + Math.random(), center.z + Math.sin(angle) * 0.4, Math.cos(angle) * 0.22, 0.04, Math.sin(angle) * 0.22);
        }
    }

    public static void onSuperchargeAura(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        Vec3 pos = entity.position();

        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points + (level.getGameTime() * 0.10);
            level.addParticle(BOLT_YELLOW, pos.x + Math.cos(angle) * 0.65, pos.y + 0.9, pos.z + Math.sin(angle) * 0.65, 0, 0.005, 0);
        }

        if (Math.random() < 0.5) {
            double angle = Math.random() * Math.PI * 2;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x + Math.cos(angle) * 0.65, pos.y + 0.9, pos.z + Math.sin(angle) * 0.65, Math.cos(angle) * 0.10, 0.04, Math.sin(angle) * 0.10);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x + Math.cos(angle) * 0.65, pos.y + 0.9, pos.z + Math.sin(angle) * 0.65, Math.cos(angle) * 0.10, 0.04, Math.sin(angle) * 0.10);
        }

        // Random Rod pop
        if (Math.random() < 0.2) {
            double angle = Math.random() * Math.PI * 2;
            level.addParticle(ParticleTypes.END_ROD, pos.x + (Math.random() - 0.5) * 0.4, pos.y + 0.6 + Math.random() * 1.2, pos.z + (Math.random() - 0.5) * 0.4, Math.cos(angle) * 0.15, 0.06, Math.sin(angle) * 0.15);
        }
    }
}