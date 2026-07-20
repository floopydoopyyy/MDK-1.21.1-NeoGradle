package com.oliver.witchmod.blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Radius-based shield; players inside repel all curses/blessings but get no feedback on blocked attempts
 * (CLAUDE.md section 2.4) — contrast with the personal Ward, which does give feedback.
 *
 * <p>Active totem positions are tracked in a plain in-memory map (not persisted) — rebuilt naturally as
 * placed totems' block entities... actually as their block states load in, since this uses onPlace/onRemove
 * rather than a BlockEntity. A server restart with totems already placed will need them to be
 * broken/replaced once to re-register; acceptable for now, revisit if that matters later.
 */
public final class WardingTotemBlock extends Block {
    private static final double RADIUS = 16.0;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_TOTEMS = new HashMap<>();

    public WardingTotemBlock(Properties properties) {
        super(properties);
    }

    public static boolean isProtected(ServerPlayer player) {
        Set<BlockPos> totems = ACTIVE_TOTEMS.get(player.level().dimension());
        if (totems == null || totems.isEmpty()) {
            return false;
        }
        BlockPos playerPos = player.blockPosition();
        return totems.stream().anyMatch(pos -> pos.distSqr(playerPos) <= RADIUS * RADIUS);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            ACTIVE_TOTEMS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            Set<BlockPos> totems = ACTIVE_TOTEMS.get(level.dimension());
            if (totems != null) {
                totems.remove(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
