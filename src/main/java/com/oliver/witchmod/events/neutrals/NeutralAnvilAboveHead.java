package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Classic. An anvil, right above your head, with nowhere to be but down. */
public final class NeutralAnvilAboveHead extends BewitchmentEvent {
    private static final int HEIGHT_ABOVE = 5;

    public NeutralAnvilAboveHead() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        BlockPos above = initiator.blockPosition().above(HEIGHT_ABOVE);
        FallingBlockEntity.fall(level, above, Blocks.ANVIL.defaultBlockState());
    }
}
