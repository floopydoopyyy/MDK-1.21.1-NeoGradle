package com.loopy.loopypowers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import org.joml.Vector3f;

public class IceSpikeBlock extends PointedDripstoneBlock {

    private static final DustParticleOptions ICE_SHIMMER = new DustParticleOptions(new Vector3f(0.55f, 0.90f, 1.00f), 0.9f);

    public IceSpikeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
                        .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.TIP)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getClickedFace() == Direction.DOWN) return null;

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos down = pos.below();

        if (!level.getBlockState(down).isFaceSturdy(level, down, Direction.UP) && !level.getBlockState(down).is(this)) {
            return null;
        }

        return this.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.TIP);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide) {
            updateColumn(level, pos, this);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);

        if (!level.isClientSide && !state.is(newState.getBlock())) {
            updateColumn(level, pos.below(), this);
            updateColumn(level, pos.above(), this);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos down = pos.below();
        BlockState below = level.getBlockState(down);

        if (below.is(this)) return true;

        return below.isFaceSturdy(level, down, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        if (state.getValue(BlockStateProperties.VERTICAL_DIRECTION) != Direction.UP) {
            state = state.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP);
        }

        if (!level.isClientSide()) {
            updateColumn(level, pos, this);
        }

        return state;
    }

    private static void updateColumn(LevelAccessor level, BlockPos start, Block spikeBlock) {
        BlockPos p = start;

        if (!level.getBlockState(p).is(spikeBlock)) {
            if (level.getBlockState(p.above()).is(spikeBlock)) p = p.above();
            else if (level.getBlockState(p.below()).is(spikeBlock)) p = p.below();
            else return;
        }

        BlockPos bottom = p;
        while (level.getBlockState(bottom.below()).is(spikeBlock)) bottom = bottom.below();

        BlockPos top = p;
        while (level.getBlockState(top.above()).is(spikeBlock)) top = top.above();

        int total = (top.getY() - bottom.getY()) + 1;

        for (int i = 0; i < total; i++) {
            BlockPos at = bottom.above(i);
            BlockState s = level.getBlockState(at);
            if (!s.is(spikeBlock)) continue;

            DripstoneThickness th;
            if (total <= 1) th = DripstoneThickness.TIP;
            else if (total == 2) th = (i == 0) ? DripstoneThickness.FRUSTUM : DripstoneThickness.TIP;
            else if (total == 3) th = (i == 0) ? DripstoneThickness.BASE : (i == 1 ? DripstoneThickness.FRUSTUM : DripstoneThickness.TIP);
            else th = (i == 0) ? DripstoneThickness.BASE : (i == 1 ? DripstoneThickness.FRUSTUM : (i == total - 1 ? DripstoneThickness.TIP : DripstoneThickness.MIDDLE));

            BlockState ns = s.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
                    .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, th);

            if (ns != s) level.setBlock(at, ns, 2);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.25;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.25;

        DripstoneThickness th = state.getValue(BlockStateProperties.DRIPSTONE_THICKNESS);
        double yBase = switch (th) {
            case TIP, TIP_MERGE -> 0.65;
            case MIDDLE -> 0.55;
            case FRUSTUM -> 0.40;
            case BASE -> 0.20;
        };

        double y = pos.getY() + yBase + random.nextDouble() * 0.15;

        if (random.nextBoolean()) {
            level.addParticle(ICE_SHIMMER, x, y, z, 0.0, 0.01, 0.0);
        }
    }

    // MELTING
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // use super so other stuff still happens
        super.randomTick(state, level, pos, random);

        // melt in ice
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11) {
            this.melt(state, level, pos);
        }
    }

    protected void melt(BlockState state, ServerLevel level, BlockPos pos) {
        // make water in non warm dimensions
        // no that old comment is a lie that was even more annoying
            level.removeBlock(pos, false);
        }
    }