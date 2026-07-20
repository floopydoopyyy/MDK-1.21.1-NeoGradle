package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Boing. You don't so much land as rebound.
 *
 * <p>PROTOTYPE: the full spec gives real restitution (bouncing off ground/entities) plus fall immunity,
 * with custom boing sounds (Section 12). The fall-damage immunity is implemented now in
 * {@link com.oliver.witchmod.effects.BlessingEventHandler} via the fall event; the actual bounce physics
 * and sounds are deferred. This class only declares cost/sacrificial item.
 */
public final class BlessingBouncy extends Effect {
    public BlessingBouncy() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.SLIME_BALL);
    }
}
