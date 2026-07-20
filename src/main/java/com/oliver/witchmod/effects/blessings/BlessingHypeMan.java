package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Someone's hyping you up, loudly, whether or not anyone else can see them. */
public final class BlessingHypeMan extends Effect {
    private static final int INTERVAL_TICKS = 500;
    private static final double RADIUS = 12.0;
    private static final String[] HYPE = {
            "LET'S GOOOO!",
            "THAT'S MY PLAYER RIGHT THERE!",
            "UNSTOPPABLE. ABSOLUTELY UNSTOPPABLE.",
            "RUN IT BACK!"
    };

    public BlessingHypeMan() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.MUSIC_DISC_CAT);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addTimedEffect(target, MobEffects.DAMAGE_BOOST, durationTicks, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeTimedEffect(target, MobEffects.DAMAGE_BOOST);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            String line = HYPE[target.getRandom().nextInt(HYPE.length)];
            level.getPlayers(p -> p.distanceToSqr(target) <= RADIUS * RADIUS)
                    .forEach(p -> p.sendSystemMessage(Component.literal(line)));
        }
    }
}
