package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** A tiny, unearned celebration, just for you. */
public final class NeutralMiniFireworkShow extends BewitchmentEvent {
    private static final int ROCKET_COUNT = 3;

    public NeutralMiniFireworkShow() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        for (int i = 0; i < ROCKET_COUNT; i++) {
            double x = initiator.getX() + (level.random.nextDouble() - 0.5) * 2.0;
            double z = initiator.getZ() + (level.random.nextDouble() - 0.5) * 2.0;
            FireworkRocketEntity rocket = new FireworkRocketEntity(level, x, initiator.getY(), z, new ItemStack(Items.FIREWORK_ROCKET));
            level.addFreshEntity(rocket);
        }
    }
}
