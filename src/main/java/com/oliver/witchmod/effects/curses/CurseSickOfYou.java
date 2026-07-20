package com.oliver.witchmod.effects.curses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Villagers nearby have had just about enough of you — though, unlike Social Outcast, they don't run. */
public final class CurseSickOfYou extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final double RADIUS = 8.0;

    public CurseSickOfYou() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.LEAD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area)) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 2.2, villager.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
        }
    }
}
