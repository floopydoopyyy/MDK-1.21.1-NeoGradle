package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Hostile mobs nearby keep losing interest in you. */
public final class BlessingPeace extends Effect {
    private static final int INTERVAL_TICKS = 40;
    private static final double RADIUS = 16.0;

    public BlessingPeace() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.POPPY);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m.getTarget() == target)) {
            mob.setTarget(null);
        }
    }
}
