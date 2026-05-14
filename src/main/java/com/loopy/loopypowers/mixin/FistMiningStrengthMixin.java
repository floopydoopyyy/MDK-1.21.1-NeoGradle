package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.network.ClientPowerState;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.StrengthPower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// allows strength people to break blocks with the power of an iron pickaxe
@Mixin(Player.class)
public abstract class FistMiningStrengthMixin {

    @Inject(
            method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void loopypowers$strengthFistHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player)(Object)this;

        // only empty-hand
        if (!self.getMainHandItem().isEmpty()) return;

        // only pickaxe-mineable blocks
        if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return;

        // don’t fake diamond-tier
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return;

        // decide "has strength" by side
        boolean hasStrength;
        if (self.level().isClientSide()) {
            hasStrength = ClientPowerState.hasStrengthPower();
        } else if (self instanceof ServerPlayer sp) {
            hasStrength = (PowerManager.getPower(sp) instanceof StrengthPower);
        } else {
            return;
        }
        if (!hasStrength) return;

        cir.setReturnValue(true);
    }
}