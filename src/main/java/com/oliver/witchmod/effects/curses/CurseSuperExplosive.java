package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * You're a bit of a hazard to be around. Getting hurt might just set you off.
 *
 * <p>The actual "explode when taking damage" trigger lives in {@link com.oliver.witchmod.effects.CurseEventHandler}
 * (it needs the incoming-damage event, not a tick), same split as Immortality's death hook. This class only
 * declares the curse's cost/sacrificial item.
 */
public final class CurseSuperExplosive extends Effect {
    public static final float ON_HIT_CHANCE = 0.08F;
    public static final float POWER = 2.5F;

    public CurseSuperExplosive() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 60, () -> Items.TNT);
    }
}
