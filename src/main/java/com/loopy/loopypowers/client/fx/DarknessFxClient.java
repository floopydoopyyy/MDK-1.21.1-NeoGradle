package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.power.DarknessPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;
import java.util.Random;

public class DarknessFxClient {
    private static final Random RNG = new Random();
    private static final DustParticleOptions BLACK_DUST =
            new DustParticleOptions(new Vector3f(0.01f, 0.01f, 0.01f), 1.8f);

    public static void spawnBlackout(double centerX, double centerY, double centerZ) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel w = client.level;
        if (w == null) return;

        // CENTER
        for(int i = 0; i < 6; i++) {
            w.addParticle(ParticleTypes.LARGE_SMOKE, centerX + (RNG.nextDouble()-0.5)*2, centerY + 1.0, centerZ + (RNG.nextDouble()-0.5)*2, 0, 0.02, 0);
        }

        // INTERIOR - Using the original 100 particles!
        for (int i = 0; i < DarknessPower.BLACKOUT_INNER_PARTICLES; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(RNG.nextDouble()) * DarknessPower.BLACKOUT_INNER_SPREAD;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double y = centerY + (RNG.nextDouble() * DarknessPower.BLACKOUT_HEIGHT * 2 - DarknessPower.BLACKOUT_HEIGHT);

            w.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.01, 0);
            if (RNG.nextFloat() < 0.35f) {
                w.addParticle(BLACK_DUST, x, y, z, 0, 0, 0);
            }
        }

        // OUTER SHELL - Using the original 20 layers and 48 points!
        for (int y = 0; y < DarknessPower.BLACKOUT_VERTICAL_LAYERS; y++) {
            // Calculate the curve of the sphere/cylinder based on height
            double heightOffset = ((double) y / (DarknessPower.BLACKOUT_VERTICAL_LAYERS - 1)) * DarknessPower.BLACKOUT_HEIGHT * 2 - DarknessPower.BLACKOUT_HEIGHT;
            double layerRadius = DarknessPower.BLACKOUT_RADIUS * Math.sqrt(1 - Math.pow(heightOffset / DarknessPower.BLACKOUT_HEIGHT, 2));

            for (int i = 0; i < DarknessPower.BLACKOUT_RING_POINTS; i++) {
                double angle = (Math.PI * 2.0 * i) / DarknessPower.BLACKOUT_RING_POINTS;
                double x = centerX + Math.cos(angle) * layerRadius;
                double z = centerZ + Math.sin(angle) * layerRadius;
                double yPos = centerY + heightOffset;

                w.addParticle(ParticleTypes.SMOKE, x, yPos, z, 0, 0, 0);
            }
        }
    }
}