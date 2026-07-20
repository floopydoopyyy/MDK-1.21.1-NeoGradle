package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * You know your way around an anvil. Repairs and enchants come cheap.
 *
 * <p>The anvil cost reduction (and lifting the "Too Expensive!" cap) is applied in
 * {@link com.oliver.witchmod.effects.BlessingEventHandler} via the anvil-update event. This class only
 * declares cost/sacrificial item.
 */
public final class BlessingBlacksmith extends Effect {
    public static final float ANVIL_COST_MULT = 0.4F;

    public BlessingBlacksmith() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.COPPER_BLOCK);
    }
}
