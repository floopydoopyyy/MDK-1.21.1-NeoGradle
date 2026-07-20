package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Everyone's laughing. Not with you. */
public final class CurseComicRelief extends Effect {
    private static final int INTERVAL_TICKS = 500;
    private static final double RADIUS = 12.0;

    public CurseComicRelief() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.TRIDENT);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            Component message = Component.literal("Everyone nearby just laughed at ").append(target.getDisplayName());
            level.getPlayers(p -> p != target && p.distanceToSqr(target) <= RADIUS * RADIUS)
                    .forEach(p -> p.sendSystemMessage(message));
        }
    }
}
