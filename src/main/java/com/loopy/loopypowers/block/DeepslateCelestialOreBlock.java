package com.loopy.loopypowers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class DeepslateCelestialOreBlock extends DropExperienceBlock {

    public DeepslateCelestialOreBlock(BlockBehaviour.Properties properties) {
        // UniformIntProvider.create() becomes UniformInt.of()
        super(UniformInt.of(3, 7), properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {

        if (random.nextFloat() > 0.3f) return;

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5);
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5);
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5);

        level.addParticle(
                ParticleTypes.END_ROD,
                x, y, z,
                0, 0.02, 0
        );
    }
}