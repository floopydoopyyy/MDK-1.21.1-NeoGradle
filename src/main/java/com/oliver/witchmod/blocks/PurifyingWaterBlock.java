package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import com.oliver.witchmod.data.EffectManager;

/** Bathing rapidly burns down active curse/blessing timers (CLAUDE.md section 2.5). */
public final class PurifyingWaterBlock extends LiquidBlock {
    /** Ticks shaved off every active curse/blessing per game tick spent in the fluid — well beyond normal countdown speed. */
    private static final int BURN_TICKS_PER_TICK = 40;

    public PurifyingWaterBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            EffectManager.reduceAllDurations(player, BURN_TICKS_PER_TICK);
        }
    }
}
