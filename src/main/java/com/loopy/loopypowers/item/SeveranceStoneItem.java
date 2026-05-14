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

public class SeveranceStoneItem extends Item {

    public SeveranceStoneItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.loopypowers.severance_stone.tooltip.flavor")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("item.loopypowers.severance_stone.tooltip.desc")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }

        if (!(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }

        // TODO: Uncomment once systems are ported
        /*
        // No power
        if (PowerManager.getPower(player) == null) {
            player.displayClientMessage(Component.translatable("message.loopypowers.severance_stone.no_power").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.pass(stack);
        }

        // don't start multiple rituals
        if (RitualManager.isActive(player)) {
            player.displayClientMessage(Component.translatable("message.loopypowers.ritual.already_active").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.pass(stack);
        }

        // begin and consume
        RitualManager.startRitual(player, RitualManager.RitualType.SEVERANCE_RITUAL);
        */

        // consume (instabuild is the Mojmap name for creativeMode)
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.success(stack);
    }
}