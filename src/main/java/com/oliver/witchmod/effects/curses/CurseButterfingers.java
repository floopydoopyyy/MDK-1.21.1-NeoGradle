package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Whatever's in your hands has a habit of just... slipping. */
public final class CurseButterfingers extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final float DROP_CHANCE = 0.25F;

    public CurseButterfingers() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.SLIME_BALL);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && EffectUtil.chance(target.getRandom(), DROP_CHANCE)) {
            if (!target.getMainHandItem().isEmpty()) {
                target.drop(target.getItemInHand(InteractionHand.MAIN_HAND).split(1), true);
            }
        }
    }
}
