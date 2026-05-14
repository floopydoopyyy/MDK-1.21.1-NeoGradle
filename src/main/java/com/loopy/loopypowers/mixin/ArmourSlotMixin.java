package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.item.ModItems;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.FlightPower;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class ArmourSlotMixin { // this stops the wings from being removed

    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"), cancellable = true)
    private void loopypowers$blockTakingWings(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = ((Slot)(Object)this).getItem();

        // Assuming DeferredRegister .get() for NeoForge
        if (!stack.is(ModItems.WINGS_OF_VALOR.get())) return;

        // Only lock it while the player currently has FlightPower
        if (player instanceof net.minecraft.server.level.ServerPlayer sp
                && PowerManager.getPower(sp) instanceof FlightPower) {
            cir.setReturnValue(false);
        }
    }
}