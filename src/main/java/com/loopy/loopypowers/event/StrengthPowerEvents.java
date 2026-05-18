package com.loopy.loopypowers.event;

import com.loopy.loopypowers.network.ClientPowerState;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.StrengthPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "loopypowers") // Fixed: Removed the deprecated bus parameter
public class StrengthPowerEvents {

    private static final float IRON_PICK_SPEED_MULT = 6.0f;
    private static final float TOOL_SPEED_MULT = 2.0f;

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();

        // Fixed: Removed the unused 'state' variable declaration here

        boolean hasStrength;
        if (player.level().isClientSide()) {
            hasStrength = ClientPowerState.hasStrengthPower();
        } else if (player instanceof ServerPlayer sp) {
            hasStrength = (PowerManager.getPower(sp) instanceof StrengthPower);
        } else {
            return;
        }

        if (!hasStrength) return;

        float currentSpeed = event.getNewSpeed();
        if (currentSpeed <= 0.0f) return; // Prevent altering unbreakable structures

        if (player.getMainHandItem().isEmpty()) {
            // Massive buff when bare-fisted
            event.setNewSpeed(currentSpeed * IRON_PICK_SPEED_MULT);
        } else {
            // General efficiency boost when utilizing tools
            event.setNewSpeed(currentSpeed * TOOL_SPEED_MULT);
        }
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();

        // Fixed: Updated to NeoForge 1.21.1's getTargetBlock()
        BlockState state = event.getTargetBlock();

        // If the tool is already correct according to vanilla rules, ignore it
        if (event.canHarvest()) return;

        boolean hasStrength;
        if (player.level().isClientSide()) {
            hasStrength = ClientPowerState.hasStrengthPower();
        } else if (player instanceof ServerPlayer sp) {
            hasStrength = (PowerManager.getPower(sp) instanceof StrengthPower);
        } else {
            return;
        }

        if (!hasStrength) return;

        // Force fists to be flagged as correct harvesting implements for primary tool blocks
        // This removes the vanilla 100x slow-fist penalty and grants true drop items!
        if (player.getMainHandItem().isEmpty()) {
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                    state.is(BlockTags.MINEABLE_WITH_AXE) ||
                    state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                    state.is(BlockTags.MINEABLE_WITH_HOE)) {
                event.setCanHarvest(true);
            }
        }
    }
}