package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Everything you do is somehow audible from three blocks further than it should be. */
public final class CurseLoud extends Effect {
    private static final int INTERVAL_TICKS = 200;

    public CurseLoud() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.GOAT_HORN);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.RAID_HORN, SoundSource.PLAYERS, 2.0F, 1.0F);
        }
    }
}
