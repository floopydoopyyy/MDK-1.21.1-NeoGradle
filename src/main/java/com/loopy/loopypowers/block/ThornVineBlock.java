package com.loopy.loopypowers.block;

// import com.loopy.loopypowers.manager.PowerManager;
// import com.loopy.loopypowers.power.NaturePower;
// import com.loopy.loopypowers.power.Power;
// import com.loopy.loopypowers.damage.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ThornVineBlock extends Block {

    // top-variant
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    // Block.box is the Mojmap equivalent of Block.createCuboidShape
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    // tuning
    private static final int DAMAGE_INTERVAL_TICKS = 8;
    private static final float DAMAGE_AMOUNT = 2.5f;
    private static final int SLOWNESS_AMP = 2;
    private static final int SLOWNESS_TICKS = 20;

    public ThornVineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        boolean isTop = !level.getBlockState(pos.above()).is(this);
        return this.defaultBlockState().setValue(TOP, isTop);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // walk through
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos downPos = pos.below();
        BlockState below = level.getBlockState(downPos);

        if (below.is(this)) return true;
        if (below.is(BlockTags.DIRT)) return true;

        // isSideSolidFullSquare becomes isFaceSturdy
        return below.isFaceSturdy(level, downPos, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        boolean isTop = !level.getBlockState(pos.above()).is(this);
        return state.setValue(TOP, isTop);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {

        if (entity instanceof ServerPlayer player) {
            // TODO: Uncomment once PowerManager is ported
            // Power p = PowerManager.getPower(player);
            // if (p instanceof NaturePower) return;
        }

        entity.makeStuckInBlock(state, new Vec3(0.70D, 0.75D, 0.70D));

        if (!(entity instanceof LivingEntity living)) return;
        if (level.isClientSide) return;

        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, SLOWNESS_AMP, true, false));

        // entity.age becomes entity.tickCount
        if (entity.tickCount % DAMAGE_INTERVAL_TICKS == 0 && level instanceof ServerLevel sl) {

            // TODO: Replace with ModDamageTypes.thorn(sl) once ported
            // boolean didHurt = living.hurt(ModDamageTypes.thorn(sl), DAMAGE_AMOUNT);
            boolean didHurt = living.hurt(sl.damageSources().sweetBerryBush(), DAMAGE_AMOUNT);

            if (didHurt) {
                sl.playSound(
                        null,
                        pos,
                        SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, // Usually maps closest to the old hurt sound
                        SoundSource.BLOCKS,
                        0.8f,
                        1.0f
                );
            }
        }
    }
}