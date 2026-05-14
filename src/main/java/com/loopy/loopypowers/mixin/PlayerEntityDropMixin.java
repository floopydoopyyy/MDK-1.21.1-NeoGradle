package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.item.ModItems;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.FlightPower;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerEntityDropMixin { // this is to stop certain items being droppable.

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void loopypowers$blockDroppingWings(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                                CallbackInfoReturnable<ItemEntity> cir) {
        Player self = (Player)(Object)this;

        if (stack.is(ModItems.WINGS_OF_VALOR.get())
                && self instanceof net.minecraft.server.level.ServerPlayer sp
                && PowerManager.getPower(sp) instanceof FlightPower) {
            cir.setReturnValue(null); // cancel drop
        }
    }
}