package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.network.ClientPowerState;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.StrengthPower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class FistMiningSpeedMixin {
    // this class just makes it so the mining is faster with strength. the other class increases the strength with the fist.
    @Unique
    private static final float IRON_PICK_SPEED_MULT = 6.0f; // multiplier for fist
    @Unique
    private static final float TOOL_SPEED_MULT = 1.8f; // multiplier for all tools

    @Inject(
            method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void loopypowers$strengthFistSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player self = (Player)(Object)this;

        // decide "has strength power" depending on side
        boolean hasStrength;
        if (self.level().isClientSide()) {
            hasStrength = ClientPowerState.hasStrengthPower();
        } else if (self instanceof ServerPlayer sp) {
            hasStrength = (PowerManager.getPower(sp) instanceof StrengthPower);
        } else {
            return;
        }
        if (!hasStrength) return;

        float current = cir.getReturnValueF();
        if (current <= 0.0f) return; // don't try to break unbreakable stuff

        // EMPTY HAND - makes it so mining speed is very fast (aimed for iron pickaxe level)
        if (self.getMainHandItem().isEmpty()) {
            if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return;
            cir.setReturnValue(current * IRON_PICK_SPEED_MULT);
            return;
        }
        // TOOL IN HAND - make it a bit quicker
        cir.setReturnValue(current * TOOL_SPEED_MULT);
    }
}