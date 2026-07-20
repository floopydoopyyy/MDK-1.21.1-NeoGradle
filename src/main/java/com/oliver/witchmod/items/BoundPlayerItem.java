package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/** Shared "Bound to: &lt;name&gt;" tooltip for Player Essence and Voodoo Doll. */
public class BoundPlayerItem extends Item {
    public BoundPlayerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        if (bound != null) {
            tooltip.add(Component.literal("Bound to: " + bound.playerName()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Unbound").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
