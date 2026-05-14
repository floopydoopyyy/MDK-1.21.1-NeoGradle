package com.loopy.loopypowers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class CelestialOreBlock extends DropExperienceBlock {

    public CelestialOreBlock(BlockBehaviour.Properties properties) {
        super(UniformInt.of(3, 7), properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {

        // Direction.random() becomes Direction.getRandom()
        Direction dir = Direction.getRandom(random);

        // getOffsetX() becomes getStepX()
        double x = pos.getX() + 0.5 + dir.getStepX() * 0.55;
        double y = pos.getY() + 0.5 + dir.getStepY() * 0.55;
        double z = pos.getZ() + 0.5 + dir.getStepZ() * 0.55;

        // face spread
        x += (random.nextDouble() - 0.5) * 0.3;
        y += (random.nextDouble() - 0.5) * 0.3;
        z += (random.nextDouble() - 0.5) * 0.3;

        // outward motion
        double vx = dir.getStepX() * 0.05;
        double vy = dir.getStepY() * 0.05;
        double vz = dir.getStepZ() * 0.05;

        // sparkle
        if (random.nextFloat() < 0.1f) {
            level.addParticle(
                    ParticleTypes.END_ROD,
                    x, y, z,
                    vx, vy + 0.02, vz
            );
        }

        // dust (DustParticleEffect becomes DustParticleOptions)
        if (random.nextFloat() < 0.5f) {
            level.addParticle(
                    new DustParticleOptions(new Vector3f(0.9f, 0.3f, 1.0f), 1.2f),
                    x, y, z,
                    vx * 0.5, vy * 0.5 + 0.01, vz * 0.5
            );
        }
    }
}