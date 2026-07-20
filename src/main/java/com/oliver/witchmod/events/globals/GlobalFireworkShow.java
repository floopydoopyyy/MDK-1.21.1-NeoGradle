package com.oliver.witchmod.events.globals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** A real show this time — every online player gets their own little display. */
public final class GlobalFireworkShow extends BewitchmentEvent {
    private static final int ROCKETS_PER_PLAYER = 6;

    public GlobalFireworkShow() {
        super(EventCategory.GLOBAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < ROCKETS_PER_PLAYER; i++) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 4.0;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 4.0;
                FireworkRocketEntity rocket = new FireworkRocketEntity(level, x, player.getY(), z, new ItemStack(Items.FIREWORK_ROCKET));
                level.addFreshEntity(rocket);
            }
        }
    }
}
