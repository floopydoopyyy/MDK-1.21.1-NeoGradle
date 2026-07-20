package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Every dropped item within reach seems to drift your way, whether you want it or not. */
public final class CurseMagnet extends Effect {
    private static final int INTERVAL_TICKS = 20;
    private static final double RADIUS = 6.0;

    public CurseMagnet() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.LODESTONE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            Vec3 toward = target.position().subtract(item.position());
            if (toward.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 pull = toward.normalize().scale(0.25);
            item.setDeltaMovement(item.getDeltaMovement().add(pull));
        }
    }
}
