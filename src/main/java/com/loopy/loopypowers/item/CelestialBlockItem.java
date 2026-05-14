package com.loopy.loopypowers.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class CelestialBlockItem extends BlockItem {

    public CelestialBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        // Gray = flavour text
        tooltip.add(Component.translatable("block.loopypowers.celestial_block.tooltip.flavor")
                .withStyle(ChatFormatting.GRAY));

        // Purple = useful info
        tooltip.add(Component.translatable("block.loopypowers.celestial_block.tooltip.info")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        super.appendHoverText(stack, context, tooltip, type);
    }
}