package com.loopy.loopypowers.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class WingsOfValorItem extends ElytraItem {

    public WingsOfValorItem(Item.Properties properties) {
        super(properties);
    }

    // ============================================================
    // ELYTRA BEHAVIOUR
    // ============================================================

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return true;
    }

    // ============================================================
    // TOOLTIP
    // ============================================================

    @SuppressWarnings("removal") // FIX: Silences the 1.21 tooltip deprecation warning
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.loopypowers.wings_of_valor.tooltip.flavor")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.loopypowers.wings_of_valor.tooltip.warning")
                .withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, context, tooltip, type);
    }
}