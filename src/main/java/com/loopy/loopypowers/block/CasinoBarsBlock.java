package com.loopy.loopypowers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class CasinoBarsBlock extends IronBarsBlock {

    // blockstate flag so you can turn particles on/off
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public CasinoBarsBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // In NeoForge/Mojmap, setDefaultState is registerDefaultState, and we get the base state from stateDefinition
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    /**
     * Client-side sparkle FX (called on the client for visible blocks).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // this method is only called client-side
        if (!level.isClientSide) return;

        // only sparkle when active
        //if (!state.getValue(ACTIVE)) return;

        // tune frequency
        if (random.nextInt(35) != 0) return;

        int count = 1 + random.nextInt(2);

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
            double y = pos.getY() + 0.2 + random.nextDouble() * 0.8;
            double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;

            double vx = (random.nextDouble() - 0.5) * 0.02;
            double vy = 0.02 + random.nextDouble() * 0.02;
            double vz = (random.nextDouble() - 0.5) * 0.02;

            level.addParticle(ParticleTypes.ENCHANT, x, y, z, vx, vy, vz);
        }
    }
}