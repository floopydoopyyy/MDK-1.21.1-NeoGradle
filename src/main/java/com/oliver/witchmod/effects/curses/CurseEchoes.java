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

/** Every sound you make comes back to you a moment later. */
public final class CurseEchoes extends Effect {
    private static final int INTERVAL_TICKS = 140;

    public CurseEchoes() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.ECHO_SHARD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }
}
