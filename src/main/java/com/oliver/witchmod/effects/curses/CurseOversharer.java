package com.oliver.witchmod.effects.curses;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You just... tell everyone exactly where you are. All the time. */
public final class CurseOversharer extends Effect {
    private static final int INTERVAL_TICKS = 600;

    public CurseOversharer() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 32, () -> Items.MAP);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            BlockPos pos = target.blockPosition();
            Component message = target.getDisplayName().copy()
                    .append(" is currently at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            level.players().forEach(p -> p.sendSystemMessage(message));
        }
    }
}
