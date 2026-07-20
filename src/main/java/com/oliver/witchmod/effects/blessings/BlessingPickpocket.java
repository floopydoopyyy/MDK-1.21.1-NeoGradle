package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You keep finding a stray emerald in your pocket. No idea where from. */
public final class BlessingPickpocket extends Effect {
    private static final int INTERVAL_TICKS = 600;

    public BlessingPickpocket() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.STRING);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.getInventory().add(new ItemStack(Items.EMERALD, 1));
        }
    }
}
