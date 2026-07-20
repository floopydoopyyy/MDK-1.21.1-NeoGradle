package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Nothing changed, visibly. Everyone nearby is nonetheless very sure something has. */
public final class CurseUgly extends Effect {
    private static final int INTERVAL_TICKS = 400;
    private static final double RADIUS = 10.0;

    public CurseUgly() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.CARVED_PUMPKIN);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            Component message = target.getDisplayName().copy().append(" looks unusually hideous right now.");
            level.getPlayers(p -> p != target && p.distanceToSqr(target) <= RADIUS * RADIUS)
                    .forEach(p -> p.sendSystemMessage(message));
        }
    }
}
