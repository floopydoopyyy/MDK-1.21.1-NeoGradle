package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** An unwarranted tip, delivered with total confidence. */
public final class NeutralMansplaining extends BewitchmentEvent {
    private static final String[] TIPS = {
            "Pro tip: torches placed every other block save resources.",
            "Pro tip: you should really keep your inventory more organized.",
            "Pro tip: creepers explode. Just so you know.",
            "Pro tip: water breaks fall damage."
    };

    public NeutralMansplaining() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        String tip = TIPS[initiator.getRandom().nextInt(TIPS.length)];
        initiator.sendSystemMessage(Component.literal(tip));
    }
}
