package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** No flavor here. Just a bit of a knock. The harshest of the neutrals (weight 3, section 5.3). */
public final class NeutralDamage extends BewitchmentEvent {
    private static final float DAMAGE_AMOUNT = 2.0F;

    public NeutralDamage() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator != null) {
            initiator.hurt(level.damageSources().generic(), DAMAGE_AMOUNT);
        }
    }
}
