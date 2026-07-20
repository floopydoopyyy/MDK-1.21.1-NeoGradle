package com.oliver.witchmod.events.neutrals;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** Turn around. */
public final class NeutralCreeperBehindPlayer extends BewitchmentEvent {
    private static final double DISTANCE_BEHIND = 3.0;

    public NeutralCreeperBehindPlayer() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        Vec3 behind = initiator.position().subtract(initiator.getLookAngle().normalize().scale(DISTANCE_BEHIND));
        BlockPos pos = BlockPos.containing(behind);
        Creeper creeper = EntityType.CREEPER.spawn(level, pos, MobSpawnType.EVENT);
        if (creeper != null) {
            creeper.moveTo(behind.x, pos.getY(), behind.z, initiator.getYRot() + 180.0F, 0.0F);
        }
    }
}
