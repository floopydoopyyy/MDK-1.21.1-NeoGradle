package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * One last surge. Take a fatal blow and get back up swinging — once.
 *
 * <p>The revive (partial health + brief Strength/Speed/Resistance + a knockback burst) and the
 * blessing-consumption behavior are applied in {@link com.oliver.witchmod.effects.BlessingEventHandler}
 * via the death event, mirroring Immortality's hook but single-use. The custom rise sound (Section 12) and
 * the knockback-explosion visuals are deferred. This class only declares cost/sacrificial item.
 */
public final class BlessingLastStand extends Effect {
    public static final float REVIVE_HEALTH = 6.0F;
    public static final int BUFF_TICKS = 300;

    public BlessingLastStand() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 50, () -> Items.ENCHANTED_GOLDEN_APPLE);
    }
}
