package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You cannot stop talking. Nearby players hear every bit of it. */
public final class CurseYap extends Effect {
    private static final int INTERVAL_TICKS = 300;
    private static final double RADIUS = 12.0;
    private static final String[] LINES = {
            "so anyway, that's basically what happened",
            "wait, wait, let me finish though",
            "okay but here's the thing",
            "not to go on a tangent but",
            "and ANOTHER thing..."
    };

    public CurseYap() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.PARROT_SPAWN_EGG);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            String line = LINES[target.getRandom().nextInt(LINES.length)];
            Component message = target.getDisplayName().copy().append(": " + line);
            level.getPlayers(p -> p.distanceToSqr(target) <= RADIUS * RADIUS)
                    .forEach(p -> p.sendSystemMessage(message));
        }
    }
}
