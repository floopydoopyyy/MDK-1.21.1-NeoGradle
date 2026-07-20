package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Everything on the ground nearby ends up stuck to you, whether you wanted it or not. */
public final class CurseSticky extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final double RADIUS = 4.0;

    public CurseSticky() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.HONEY_BLOCK);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(RADIUS);
        level.getEntitiesOfClass(ItemEntity.class, area).stream().findAny().ifPresent(item -> {
            if (!item.getItem().isEmpty() && target.getInventory().add(item.getItem())) {
                item.discard();
            }
        });
    }
}
