package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.network.payload.TelekinesisParticlePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class TelekinesisFxClient {

    private static final DustParticleOptions TK_PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.7f), 1.2f);
    private static final DustParticleOptions TK_LIGHT_PINK = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.85f), 0.9f);
    private static final DustParticleOptions TK_MAGENTA = new DustParticleOptions(new Vector3f(0.85f, 0.0f, 0.5f), 1.5f);
    private static final DustParticleOptions TK_PINK_LARGE = new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.75f), 2.2f);
    private static final DustParticleOptions TK_DARK_PINK = new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.35f), 1.8f);

    public static void onParticleEvent(TelekinesisParticlePayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        double x = p.x(), y = p.y(), z = p.z();
        double tx = p.tx(), ty = p.ty(), tz = p.tz();

        switch (p.eventId()) {
            case TelekinesisParticlePayload.YANK_CAST, TelekinesisParticlePayload.SUSPEND_CAST ->
                    spawnBeam(level, new Vec3(x, y, z), new Vec3(tx, ty, tz));

            case TelekinesisParticlePayload.IMPACT_RING ->
                    spawnImpactRing(level, new Vec3(x, y, z), (int)tx, ty);

            case TelekinesisParticlePayload.SUSPEND_TICK -> {
                if (level.getEntity(p.entityId()) instanceof LivingEntity le) {
                    long time = level.getGameTime();
                    double radius = 0.7;
                    for (int i = 0; i < 6; i++) {
                        double angle = (time * 0.12) + (i * Math.PI * 2.0 / 6);
                        spawnParticles(level, TK_PINK,
                                le.getX() + Math.cos(angle) * radius, le.getY(0.6), le.getZ() + Math.sin(angle) * radius,
                                1, 0, 0.02, 0, 0);
                    }
                    if (time % 3 == 0) {
                        spawnParticles(level, TK_LIGHT_PINK,
                                le.getX(), le.getY(0.5), le.getZ(),
                                2, 0.25, 0.1, 0.25, 0.01);
                    }
                }
            }
            case TelekinesisParticlePayload.CHOKE_TICK -> {
                if (level.getEntity(p.entityId()) instanceof LivingEntity le) {
                    long time = level.getGameTime();
                    float chokeProgress = p.data();
                    double radius = 0.5 + (0.2 - 0.5) * chokeProgress;
                    double spinSpeed = 0.12 + chokeProgress * 0.30;
                    int points = 8;

                    for (int i = 0; i < points; i++) {
                        double angle = (time * spinSpeed) + (i * Math.PI * 2.0 / points);
                        spawnParticles(level, TK_DARK_PINK,
                                le.getX() + Math.cos(angle) * radius, le.getY(0.3 + chokeProgress * 0.4), le.getZ() + Math.sin(angle) * radius,
                                1, 0, 0.01, 0, 0);
                    }

                    if (chokeProgress > 0.5f) {
                        double innerRadius = radius * 0.4;
                        for (int i = 0; i < 4; i++) {
                            double angle = -(time * spinSpeed * 1.5) + (i * Math.PI / 2.0);
                            spawnParticles(level, TK_MAGENTA,
                                    le.getX() + Math.cos(angle) * innerRadius, le.getY(0.5), le.getZ() + Math.sin(angle) * innerRadius,
                                    1, 0, 0.02, 0, 0);
                        }
                    }

                    if (time % 2 == 0) {
                        spawnParticles(level, TK_DARK_PINK, le.getX(), le.getY(0.8), le.getZ(), 2, 0.15, 0.08, 0.15, 0.03);
                    }
                }
            }
            case TelekinesisParticlePayload.WALL_IMPACT -> spawnWallImpact(level, new Vec3(x, y, z));
            case TelekinesisParticlePayload.FLOOR_IMPACT -> spawnFloorImpact(level, new Vec3(x, y, z));
            case TelekinesisParticlePayload.SLAM_IMPACT -> spawnSlamImpact(level, new Vec3(x, y, z));
            case TelekinesisParticlePayload.ULTIMATE_CAST -> {
                for (int ring = 0; ring < 3; ring++) {
                    double ringRadius = 1.5 + ring * 2.5;
                    int points = 12 + ring * 6;
                    for (int i = 0; i < points; i++) {
                        double angle = Math.PI * 2.0 * i / points;
                        spawnParticles(level, ring % 2 == 0 ? TK_PINK_LARGE : TK_MAGENTA,
                                x + Math.cos(angle) * ringRadius, y, z + Math.sin(angle) * ringRadius,
                                1, 0, 0.12, 0, 0.03);
                    }
                }
                for (int i = 0; i < 20; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = level.random.nextDouble() * 1.5;
                    spawnParticles(level, TK_DARK_PINK,
                            x + Math.cos(a) * r, y - 0.2 + level.random.nextDouble() * 2.0, z + Math.sin(a) * r,
                            1, 0, 0.15, 0, 0.04);
                }
            }
            case TelekinesisParticlePayload.DEBRIS_HARVEST -> spawnParticles(level, TK_MAGENTA, x, y, z, 10, 0.3, 0.3, 0.3, 0.05);
            case TelekinesisParticlePayload.DEBRIS_TICK -> {
                Entity player = level.getEntity(p.entityId());
                if (player != null) {
                    long time = level.getGameTime();
                    Vec3 playerPos = new Vec3(x, y, z);

                    if (time % 2 == 0) {
                        for (int i = 0; i < 3; i++) {
                            double angle = level.random.nextDouble() * Math.PI * 2;
                            double r = 2.5 + level.random.nextDouble() * 2.0;
                            spawnParticles(level, TK_DARK_PINK,
                                    player.getX() + Math.cos(angle) * r, player.getY() + level.random.nextDouble() * 2.5, player.getZ() + Math.sin(angle) * r,
                                    1, 0.1, 0.1, 0.1, 0.05);
                        }
                    }

                    for (int ring = 0; ring < 3; ring++) {
                        double ringR = 2.5 + ring * 0.8;
                        double ringSpeed = 0.06 + ring * 0.02;
                        double ringDir = (ring % 2 == 0) ? 1 : -1;
                        int auraPoints = 4 + ring * 2;

                        for (int i = 0; i < auraPoints; i++) {
                            double a = (time * ringSpeed * ringDir) + (i * Math.PI * 2.0 / auraPoints);
                            spawnParticles(level, ring == 0 ? TK_MAGENTA : ring == 1 ? TK_PINK : TK_LIGHT_PINK,
                                    playerPos.x + Math.cos(a) * ringR, playerPos.y + Math.sin(a * 0.5) * 0.3, playerPos.z + Math.sin(a) * ringR,
                                    1, 0, 0.015, 0, 0);
                        }
                    }
                }
            }
            case TelekinesisParticlePayload.PULL_TICK -> spawnParticles(level, TK_LIGHT_PINK, x, y, z, 1, tx, ty, tz, 0.01);
            case TelekinesisParticlePayload.ORBIT_TICK -> {
                boolean isInner = p.data() > 0;
                spawnParticles(level, isInner ? TK_MAGENTA : TK_DARK_PINK, x, y, z, 1, 0.04, 0.04, 0.04, 0.01);
            }
            case TelekinesisParticlePayload.PASSIVE_HIT -> {
                spawnImpactRing(level, new Vec3(x, y, z), 10, 0.3);
                spawnParticles(level, TK_LIGHT_PINK, x, y, z, 4, 0.2, 0.3, 0.2, 0.03);
            }
            case TelekinesisParticlePayload.DEBRIS_EXPLOSION -> {
                spawnSlamImpact(level, new Vec3(x, y, z));
                spawnParticles(level, ParticleTypes.EXPLOSION, x, y + 0.5, z, 3, 0.5, 0.3, 0.5, 0.1);
                for (int i = 0; i < 15; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = level.random.nextDouble() * 0.8;
                    spawnParticles(level, TK_DARK_PINK,
                            x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r,
                            1, Math.cos(a) * 0.3, 0.2, Math.sin(a) * 0.3, 0.05);
                }
            }
            case TelekinesisParticlePayload.YANK_FALL_DAMAGE -> spawnParticles(level, TK_MAGENTA, x, y, z, 15, 0.4, 0.2, 0.4, 0.05);
            case TelekinesisParticlePayload.THROW_RELEASE -> {
                spawnImpactRing(level, new Vec3(x, y, z), 14, 0.4);
                spawnParticles(level, TK_MAGENTA, x, y + 0.5, z, 8, 0.3, 0.3, 0.3, 0.06);
            }
            case TelekinesisParticlePayload.DEBRIS_THROW -> {
                spawnImpactRing(level, new Vec3(x, y, z), 8, 0.25);
                spawnParticles(level, TK_MAGENTA, x, y, z, 4, 0.2, 0.2, 0.2, 0.06);
            }
        }
    }

    private static void spawnBeam(ClientLevel world, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        int steps = (int)(dir.length() / 0.35);
        Vec3 step = dir.normalize().scale(0.35);
        Vec3 pos = from;
        for (int i = 0; i < steps; i++) {
            if (i % 2 == 0) spawnParticles(world, TK_PINK, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0);
            if (world.random.nextFloat() < 0.2f) spawnParticles(world, TK_LIGHT_PINK, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            pos = pos.add(step);
        }
    }

    private static void spawnImpactRing(ClientLevel world, Vec3 pos, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            spawnParticles(world, TK_MAGENTA,
                    pos.x, pos.y + 0.1, pos.z,
                    1, Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius, 0.04);
        }
        spawnParticles(world, TK_PINK, pos.x, pos.y + 0.5, pos.z, 6, 0.3, 0.3, 0.3, 0.05);
    }

    private static void spawnSlamImpact(ClientLevel world, Vec3 pos) {
        spawnImpactRing(world, pos, 24, 0.5);
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2.0 * i / 20;
            spawnParticles(world, TK_PINK_LARGE,
                    pos.x + Math.cos(angle) * 1.5, pos.y + 0.1, pos.z + Math.sin(angle) * 1.5,
                    1, Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15, 0.02);
        }
        spawnParticles(world, TK_MAGENTA, pos.x, pos.y + 0.3, pos.z, 12, 0.5, 0.4, 0.5, 0.08);
        spawnParticles(world, ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 8, 0.4, 0.3, 0.4, 0.06);
    }

    private static void spawnWallImpact(ClientLevel world, Vec3 pos) {
        spawnImpactRing(world, pos, 18, 0.4);
        spawnParticles(world, TK_MAGENTA, pos.x, pos.y + 0.5, pos.z, 10, 0.4, 0.4, 0.4, 0.07);
        spawnParticles(world, TK_PINK_LARGE, pos.x, pos.y + 0.3, pos.z, 5, 0.3, 0.3, 0.3, 0.04);

        for (int i = 0; i < 12; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.1 + world.random.nextDouble() * 0.2;
            spawnParticles(world, TK_DARK_PINK,
                    pos.x, pos.y + 0.5, pos.z,
                    1, Math.cos(angle) * speed, (world.random.nextDouble() - 0.3) * speed, Math.sin(angle) * speed, 0.01);
        }
    }

    private static void spawnFloorImpact(ClientLevel world, Vec3 pos) {
        spawnImpactRing(world, pos, 12, 0.35);
        spawnParticles(world, TK_PINK, pos.x, pos.y + 0.2, pos.z, 5, 0.3, 0.1, 0.3, 0.04);
    }

    private static void spawnParticles(ClientLevel level, ParticleOptions type, double x, double y, double z, int count, double xDist, double yDist, double zDist, double maxSpeed) {
        RandomSource random = level.random;
        if (count == 0) {
            level.addParticle(type, x, y, z, xDist, yDist, zDist);
            return;
        }
        for (int i = 0; i < count; i++) {
            level.addParticle(type,
                    x + random.nextGaussian() * xDist, y + random.nextGaussian() * yDist, z + random.nextGaussian() * zDist,
                    random.nextGaussian() * maxSpeed, random.nextGaussian() * maxSpeed, random.nextGaussian() * maxSpeed);
        }
    }
}