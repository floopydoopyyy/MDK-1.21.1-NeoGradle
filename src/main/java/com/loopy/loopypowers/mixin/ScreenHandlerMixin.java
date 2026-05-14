package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.item.ModItems;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.FlightPower;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class ScreenHandlerMixin { // i FUCKING HATE MIXINS

    @Inject(
            method = "clicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void loopypowers$blockMovingWings(
            int slotId, int button, ClickType clickType, Player player,
            CallbackInfo ci
    ) {
        if (player.level().isClientSide()) return;

        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!(PowerManager.getPower(sp) instanceof FlightPower)) return;

        AbstractContainerMenu self = (AbstractContainerMenu)(Object)this;

        // Block if cursor holding wings
        ItemStack cursor = self.getCarried();
        if (cursor.is(ModItems.WINGS_OF_VALOR.get())) {
            ci.cancel();
            return;
        }

        // Block if clicked slot contains wings
        if (slotId >= 0 && slotId < self.slots.size()) {
            Slot slot = self.slots.get(slotId);
            if (slot != null && slot.hasItem() && slot.getItem().is(ModItems.WINGS_OF_VALOR.get())) {
                ci.cancel();
            }
        }
    }
}