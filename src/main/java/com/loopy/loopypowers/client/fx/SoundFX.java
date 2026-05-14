package com.loopy.loopypowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SoundFX {

    // RESONANCE TRAIL BUFFER (client only)
    private static final Map<Integer, ArrayDeque<ResTrailPoint>> RES_TRAILS = new HashMap<>();
    private static final int RES_TRAIL_LIFETIME_TICKS = 150; // 3 seconds
    private static final int RES_TRAIL_MAX_POINTS = 90;     // cap per entity so it doesn't explode

    private static final DustParticleOptions RES_BLUE_DUST =
            new DustParticleOptions(new Vector3f(0.15f, 0.55f, 1.00f), 0.75f);

    private static final Map<Integer, Integer> RES_LINES = new HashMap<>(); // targetId -> ticksLeft

    private static final class ResTrailPoint {
        final double x, y, z;
        int age;
        float intensity;
        ResTrailPoint(double x, double y, double z, float intensity) {
            this.x = x; this.y = y; this.z = z;
            this.intensity = intensity;
            this.age = 0;
        }
    }

    public static void addTrailPoints(int targetId, int count, float intensity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var e = client.level.getEntity(targetId);
        if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return;

        Vec3 vel = le.getDeltaMovement();
        Vec3 back = vel.lengthSqr() > 1.0e-4 ? vel.normalize().scale(-0.35) : new Vec3(0, 0, 0);
        int n = Math.max(1, Math.min(10, (int) (count * Mth.clamp(intensity, 0.6f, 1.6f))));
        ArrayDeque<ResTrailPoint> dq = RES_TRAILS.computeIfAbsent(targetId, k -> new ArrayDeque<>());

        for (int i = 0; i < n; i++) {
            double jx = (client.level.random.nextDouble() - 0.5) * 0.45;
            double jy = client.level.random.nextDouble() * (le.getBbHeight() * 0.9);
            double jz = (client.level.random.nextDouble() - 0.5) * 0.45;

            double px = le.getX() + back.x + jx;
            double py = le.getY() + 0.10 + jy;
            double pz = le.getZ() + back.z + jz;

            dq.addLast(new ResTrailPoint(px, py, pz, intensity));
        }
        while (dq.size() > RES_TRAIL_MAX_POINTS) dq.removeFirst();
    }

    public static void spawnRing(int targetId, float intensity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var e = client.level.getEntity(targetId);
        if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return;

        double cx = le.getX();
        double cy = le.getY() + le.getBbHeight() * 0.55;
        double cz = le.getZ();

        int points = 18;
        double radius = 0.9 + (Mth.clamp(intensity, 0.6f, 1.6f) - 1.0) * 0.25;

        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            double x = cx + Math.cos(a) * radius;
            double z = cz + Math.sin(a) * radius;

            client.level.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, cy, z, 0.0, 0.0, 0.0);
            if (client.level.random.nextFloat() < 0.35f) {
                client.level.addParticle(ParticleTypes.SCULK_SOUL, x, cy + (client.level.random.nextDouble() - 0.5) * 0.35, z, 0.0, 0.0, 0.0);
            }
        }
    }

    public static void setLine(int targetId, int ticks) {
        RES_LINES.put(targetId, Math.max(RES_LINES.getOrDefault(targetId, 0), ticks));
    }

    public static void tick(Minecraft client) {
        tickResonanceTrails(client);
        tickResonanceLines(client);
    }

    private static void tickResonanceTrails(Minecraft client) {
        if (client.level == null) return;

        Iterator<Map.Entry<Integer, ArrayDeque<ResTrailPoint>>> it = RES_TRAILS.entrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            ArrayDeque<ResTrailPoint> dq = entry.getValue();
            if (dq.isEmpty()) { it.remove(); continue; }

            // age it
            Iterator<ResTrailPoint> pit = dq.iterator();
            while (pit.hasNext()) {
                ResTrailPoint p = pit.next();
                p.age++;
                if (p.age >= RES_TRAIL_LIFETIME_TICKS) pit.remove();
            }

            if (dq.isEmpty()) { it.remove(); continue; }

            // re-emit a few particles each tick so it lingers
            // scale a bit with intensity, but keep it capped
            ResTrailPoint newest = dq.peekLast();
            float intensity = newest != null ? newest.intensity : 1.0f;

            int emit = Math.max(1, Math.min(6, (int)(2 * Mth.clamp(intensity, 0.6f, 1.6f))));

            // bias to newer points, but not always the exact same point
            int spreadPick = Math.min(dq.size(), 8);

            for (int k = 0; k < emit; k++) {
                ResTrailPoint p = dq.peekLast();
                if (p == null) break;

                // occasionally pick a slightly older point so it is still like seeable.
                if (spreadPick > 1 && client.level.random.nextFloat() < 0.45f) {
                    int skip = client.level.random.nextInt(spreadPick);
                    Iterator<ResTrailPoint> sit = dq.descendingIterator();
                    ResTrailPoint chosen = p;
                    for (int s = 0; s <= skip && sit.hasNext(); s++) chosen = sit.next();
                    p = chosen;
                }

                // slight jitter so it looks like a smudge not a dotted line
                double jx = (client.level.random.nextDouble() - 0.5) * 0.18;
                double jy = (client.level.random.nextDouble() - 0.5) * 0.12;
                double jz = (client.level.random.nextDouble() - 0.5) * 0.18;

                client.level.addParticle(
                        RES_BLUE_DUST,
                        p.x + jx,
                        p.y + jy,
                        p.z + jz,
                        0.0, 0.0, 0.0
                );
            }
        }
    }

    private static void tickResonanceLines(Minecraft client) {
        if (client.level == null) return;
        if (client.player == null) return;

        var it = RES_LINES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            int targetId = entry.getKey();
            int left = entry.getValue() - 1;

            if (left <= 0) {
                it.remove();
                continue;
            }
            entry.setValue(left);

            var e = client.level.getEntity(targetId);
            if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) continue;

            // draw line from NOT EYE! it was annoying
            Vec3 a = client.player.position().add(0, 0.20, 0);
            Vec3 b = le.position().add(0, le.getBbHeight() * 0.35, 0);

            Vec3 delta = b.subtract(a);
            double len = delta.length();
            if (len < 0.001) continue;

            int steps = Mth.clamp((int)(len * 10), 10, 80); // density along line
            Vec3 step = delta.scale(1.0 / steps);

            Vec3 p = a;
            for (int i = 0; i <= steps; i++) {
                // make it a bit variable
                double jx = (client.level.random.nextDouble() - 0.5) * 0.06;
                double jy = (client.level.random.nextDouble() - 0.5) * 0.06;
                double jz = (client.level.random.nextDouble() - 0.5) * 0.06;

                client.level.addParticle(
                        RES_BLUE_DUST,
                        p.x + jx, p.y + jy, p.z + jz,
                        0.0, 0.0, 0.0
                );

                p = p.add(step);
            }
        }
    }
}