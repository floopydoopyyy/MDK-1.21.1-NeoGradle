package com.loopy.loopypowers.item;

import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.ritual.RitualManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class PerfectedCoreItem extends Item {

    public PerfectedCoreItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.loopypowers.perfected_celestial_core.tooltip.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("item.loopypowers.perfected_celestial_core.tooltip.effect")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (!(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }

        // Check if they even have a power
        if (PowerManager.getPower(player) == null) {
            player.displayClientMessage(
                    Component.translatable("message.loopypowers.perfected_core.no_power").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        // check if ritual is active
        if (RitualManager.isActive(player)) {
            player.displayClientMessage(
                    Component.translatable("message.loopypowers.ritual.already_active").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        int playerLevel = PowerManager.getLevel(player);

        // requires 2
        if (playerLevel != 2) {
            if (playerLevel >= 3) {
                player.displayClientMessage(
                        Component.translatable("message.loopypowers.perfected_core.level_maxed").withStyle(ChatFormatting.RED),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.translatable("message.loopypowers.perfected_core.level_too_low").withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResultHolder.fail(stack);
        }

        // Apply it
        RitualManager.startRitual(player, RitualManager.RitualType.PERFECTED_UPGRADE);

        // Consume item
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.success(stack);
    }
}