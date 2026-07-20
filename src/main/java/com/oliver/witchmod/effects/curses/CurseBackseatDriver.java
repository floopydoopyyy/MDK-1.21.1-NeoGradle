package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** "You should've turned back there." Unsolicited driving advice, whenever you're riding anything. */
public final class CurseBackseatDriver extends Effect {
    private static final int INTERVAL_TICKS = 100;
    private static final String[] ADVICE = {
            "You should've turned back there.",
            "I would've gone a different way.",
            "Are you sure this is the fastest route?",
            "Watch out for that!"
    };

    public CurseBackseatDriver() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.SADDLE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        Entity vehicle = target.getVehicle();
        if (vehicle != null && EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            String line = ADVICE[target.getRandom().nextInt(ADVICE.length)];
            target.displayClientMessage(Component.literal(line), true);
            var random = target.getRandom();
            vehicle.push((random.nextDouble() - 0.5) * 0.1, 0, (random.nextDouble() - 0.5) * 0.1);
        }
    }
}
