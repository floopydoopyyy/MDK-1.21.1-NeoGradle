package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * A comedy trumpet scores your every step. Wah, wah, waaah.
 *
 * <p>PROTOTYPE: the real curse loops a fat-trumpet walking track that starts/stops with movement, needing
 * a custom OGG (Section 12) and movement tracking. As a stand-in this periodically toots a vanilla note to
 * nearby players so the effect is audible and testable; the movement-gated custom loop is deferred.
 */
public final class CurseTrumpet extends Effect {
    private static final int INTERVAL_TICKS = 60;

    public CurseTrumpet() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.COOKIE);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            target.serverLevel().playSound(null, target.blockPosition(),
                    SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }
}
