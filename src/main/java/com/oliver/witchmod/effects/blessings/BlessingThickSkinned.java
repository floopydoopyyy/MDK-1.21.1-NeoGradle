package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * The little stuff bounces off. Only real hits get through.
 *
 * <p>The damage floor (single events of {@value #DAMAGE_FLOOR} or less are fully negated) is applied in
 * {@link com.oliver.witchmod.effects.BlessingEventHandler} via the incoming-damage event. This class only
 * declares cost/sacrificial item.
 */
public final class BlessingThickSkinned extends Effect {
    public static final float DAMAGE_FLOOR = 2.0F;

    public BlessingThickSkinned() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.ARMADILLO_SCUTE);
    }
}
