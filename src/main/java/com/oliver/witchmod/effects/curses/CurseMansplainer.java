package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** "Well, actually..." You cannot stop yourself. */
public final class CurseMansplainer extends Effect {
    private static final int INTERVAL_TICKS = 400;
    private static final double RADIUS = 12.0;
    private static final String[] LINES = {
            "Well, actually, that's not quite right.",
            "Well, actually, I've read a lot about this.",
            "Well, actually, statistically speaking...",
            "Well, actually, let me explain how that works."
    };

    public CurseMansplainer() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.BOOK);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            String line = LINES[target.getRandom().nextInt(LINES.length)];
            Component message = target.getDisplayName().copy().append(": " + line);
            level.getPlayers(p -> p != target && p.distanceToSqr(target) <= RADIUS * RADIUS)
                    .forEach(p -> p.sendSystemMessage(message));
        }
    }
}
