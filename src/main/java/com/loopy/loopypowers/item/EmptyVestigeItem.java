package com.loopy.loopypowers.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class EmptyVestigeItem extends Item {

    public EmptyVestigeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        // Gray = flavour text
        tooltip.add(Component.translatable("item.loopypowers.empty_vestige.tooltip.flavor")
                .withStyle(ChatFormatting.GRAY));
        // Purple = useful info
        tooltip.add(Component.translatable("item.loopypowers.empty_vestige.tooltip.info")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        super.appendHoverText(stack, context, tooltip, type);
    }
}