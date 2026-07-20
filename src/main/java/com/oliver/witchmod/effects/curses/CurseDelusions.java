package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Nothing happened. You just felt like something did. */
public final class CurseDelusions extends Effect {
    private static final int INTERVAL_TICKS = 240;
    private static final String[] DELUSIONS = {
            "You feel like you just found something amazing.",
            "Someone was talking about you just now. Probably.",
            "You could've sworn you heard your name.",
            "You feel like you're being watched.",
            "That was definitely a sign of something."
    };

    public CurseDelusions() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.ENDER_PEARL);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            String line = DELUSIONS[target.getRandom().nextInt(DELUSIONS.length)];
            target.displayClientMessage(Component.literal(line), true);
        }
    }
}
