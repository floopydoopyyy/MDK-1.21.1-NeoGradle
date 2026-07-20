package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** The water just... holds you up. */
public final class BlessingJesus extends Effect {
    private static final int INTERVAL_TICKS = 1;

    public BlessingJesus() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.LILY_PAD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS) && target.isInWater()) {
            Vec3 motion = target.getDeltaMovement();
            if (motion.y < 0) {
                target.setDeltaMovement(motion.x, 0, motion.z);
            }
        }
    }
}
