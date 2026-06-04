package com.loopy.loopypowers.block;

import com.loopy.loopypowers.manager.PowerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class CelestialBlock extends Block {

    private static final long COOLDOWN_REDUCTION_PER_TICK = 60; //

    public CelestialBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /* ============================================================
       COOLDOWN EFFECT & PARTICLES
       ============================================================ */

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);

            // logic
            if (entity instanceof ServerPlayer player) {
                if (PowerManager.getPower(player) == null) return;

                spawnCooldownParticles(player, (ServerLevel) level);
                PowerManager.reduceAllCooldowns(player, COOLDOWN_REDUCTION_PER_TICK);
            }
        }


    private void spawnCooldownParticles(ServerPlayer player, ServerLevel level) {
        if (player.tickCount % 3 != 0) return;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        DustParticleOptions purple = new DustParticleOptions(new Vector3f(0.7f, 0.3f, 1.0f), 0.8f);

        int points = 4;

        for (int i = 0; i < points; i++) {
            double angle = (player.tickCount * 0.08) + (i * Math.PI * 2 / points);
            double radius = 0.35;

            double x = px + Math.cos(angle) * radius;
            double z = pz + Math.sin(angle) * radius;
            double y = py + 0.2 + (i * 0.08);

            double vx = Math.cos(angle) * 0.01;
            double vz = Math.sin(angle) * 0.01;

            level.sendParticles(purple, x, y, z, 1, vx, 0.02, vz, 0.0);
        }
    }

    /* ============================================================
       AMBIENT PARTICLES
       ============================================================ */

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction dir = Direction.getRandom(random);

        double x = pos.getX() + 0.5 + dir.getStepX() * 0.55;
        double y = pos.getY() + 0.5 + dir.getStepY() * 0.55;
        double z = pos.getZ() + 0.5 + dir.getStepZ() * 0.55;

        x += (random.nextDouble() - 0.5) * 0.3;
        y += (random.nextDouble() - 0.5) * 0.3;
        z += (random.nextDouble() - 0.5) * 0.3;

        double vx = dir.getStepX() * 0.05;
        double vy = dir.getStepY() * 0.05;
        double vz = dir.getStepZ() * 0.05;

        if (random.nextFloat() < 0.15f) {
            level.addParticle(
                    ParticleTypes.END_ROD,
                    x, y, z,
                    vx, vy + 0.02, vz
            );
        }

        if (random.nextFloat() < 0.8f) {
            level.addParticle(
                    new DustParticleOptions(new Vector3f(0.9f, 0.3f, 1.0f), 1.2f),
                    x, y, z,
                    vx * 0.5, vy * 0.5 + 0.01, vz * 0.5
            );
        }
    }
}