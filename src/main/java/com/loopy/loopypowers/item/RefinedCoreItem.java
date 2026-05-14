package com.loopy.loopypowers.item;

// import com.loopy.loopypowers.manager.PowerManager;
// import com.loopy.loopypowers.ritual.RitualManager;
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

public class RefinedCoreItem extends Item {

    public RefinedCoreItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) { // hasGlint becomes isFoil
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.loopypowers.refined_celestial_core.tooltip.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("item.loopypowers.refined_celestial_core.tooltip.effect")
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

        // TODO: Uncomment once systems are ported
        /*
        // no power
        if (PowerManager.getPower(player) == null) {
            player.displayClientMessage(
                    Component.translatable("message.loopypowers.refined_core.no_power").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        int playerLevel = PowerManager.getLevel(player);

        // too high
        if (playerLevel > 1) {
            player.displayClientMessage(
                    Component.translatable("message.loopypowers.refined_core.level_too_high").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        // somehow below 1
        if (playerLevel < 1) {
            player.displayClientMessage(
                    Component.translatable("message.loopypowers.refined_core.exception_low").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        // apply it
        RitualManager.startRitual(player, RitualManager.RitualType.REFINED_UPGRADE);
        */

        // consume item
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.success(stack);
    }
}