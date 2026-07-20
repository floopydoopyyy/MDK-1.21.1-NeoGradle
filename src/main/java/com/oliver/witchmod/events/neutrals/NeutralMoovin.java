package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** You feel an overwhelming, brief urge to say "Moo." */
public final class NeutralMoovin extends BewitchmentEvent {
    public NeutralMoovin() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(), SoundEvents.COW_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
        initiator.displayClientMessage(Component.literal("Moo."), true);
    }
}
