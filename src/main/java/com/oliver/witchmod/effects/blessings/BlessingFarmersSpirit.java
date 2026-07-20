package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Green thumb. Everything growing near you takes the hint and hurries up. */
public final class BlessingFarmersSpirit extends Effect {
    private static final int INTERVAL_TICKS = 40;
    private static final int RADIUS = 12;
    private static final int GROWTHS_PER_TICK = 3;

    public BlessingFarmersSpirit() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CARROT);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        BlockPos origin = target.blockPosition();
        for (int i = 0; i < GROWTHS_PER_TICK; i++) {
            BlockPos pos = origin.offset(
                    level.random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    level.random.nextInt(5) - 2,
                    level.random.nextInt(RADIUS * 2 + 1) - RADIUS);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, pos, state)
                    && bonemealable.isBonemealSuccess(level, level.random, pos, state)) {
                bonemealable.performBonemeal(level, level.random, pos, state);
            }
        }
    }
}
