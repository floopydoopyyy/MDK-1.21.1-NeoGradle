package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Every creature that would normally leave you alone has decided today is not that day. */
public final class CurseNeutralMobsAttackInstantly extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final double RADIUS = 16.0;

    public CurseNeutralMobsAttackInstantly() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.SPIDER_EYE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m.getTarget() == null)) {
            mob.setTarget(target);
        }
    }
}
