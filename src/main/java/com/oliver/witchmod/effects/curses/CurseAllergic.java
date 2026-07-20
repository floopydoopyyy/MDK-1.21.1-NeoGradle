package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Sneezing fits, out of nowhere, at the worst possible times. */
public final class CurseAllergic extends Effect {
    private static final int INTERVAL_TICKS = 300;
    private static final int BURST_TICKS = 30;

    public CurseAllergic() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SWEET_BERRIES);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            EffectUtil.addTimedEffect(target, MobEffects.BLINDNESS, BURST_TICKS, 0);
        }
    }
}
