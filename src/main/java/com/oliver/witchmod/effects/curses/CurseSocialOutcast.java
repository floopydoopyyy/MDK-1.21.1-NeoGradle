package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Villagers take one look at you and remember they have somewhere else to be. */
public final class CurseSocialOutcast extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final double RADIUS = 8.0;

    public CurseSocialOutcast() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 35, () -> Items.WITHER_ROSE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area)) {
            Vec3 away = villager.position().subtract(target.position());
            if (away.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 push = away.normalize().scale(0.4);
            villager.push(push.x, 0, push.z);
        }
    }
}
