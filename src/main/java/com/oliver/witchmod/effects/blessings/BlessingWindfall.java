package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** A little extra shows up in your inventory every so often. */
public final class BlessingWindfall extends Effect {
    private static final int INTERVAL_TICKS = 400;

    public BlessingWindfall() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.PAPER);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 2));
        }
    }
}
