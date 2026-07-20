package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Something about you makes everyone want to take a step back. */
public final class CurseRepel extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final double RADIUS = 3.0;

    public CurseRepel() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.SLIME_BLOCK);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != target)) {
            Vec3 away = nearby.position().subtract(target.position());
            if (away.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 push = away.normalize().scale(0.6).add(0, 0.1, 0);
            nearby.push(push.x, push.y, push.z);
        }
    }
}
