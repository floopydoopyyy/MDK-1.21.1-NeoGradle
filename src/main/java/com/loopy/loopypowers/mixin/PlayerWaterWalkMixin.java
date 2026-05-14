package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.power.SpeedPower;
import com.loopy.loopypowers.network.ClientPowerState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerWaterWalkMixin {

    @Inject(
            method = "canStandOnFluid(Lnet/minecraft/world/level/material/FluidState;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void loopypowers$waterWalkInOverdrive(FluidState state, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof Player player)) return;
        if (!state.is(FluidTags.WATER)) return;

        // new check i forgot because im idiot
        boolean isOverdriveActive;
        if (player.level().isClientSide()) {
            // Predicts fluid collision on the client to stop the player falling through instantly
            isOverdriveActive = ClientPowerState.isOverdriveActive();
        } else {
            // Accurate server logic
            isOverdriveActive = SpeedPower.isOverdriveActive((ServerPlayer) player);
        }

        if (isOverdriveActive) {
            cir.setReturnValue(true);
        }
    }
}