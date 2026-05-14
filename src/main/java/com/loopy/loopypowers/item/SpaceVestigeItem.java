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

public class SpaceVestigeItem extends Item {

    public SpaceVestigeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) { // hasGlint becomes isFoil
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.loopypowers.space_vestige.tooltip.flavor")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("item.loopypowers.space_vestige.tooltip.desc")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        tooltip.add(Component.translatable("item.loopypowers.vestige.tooltip.available")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.loopypowers.space_vestige.tooltip.powers")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (!level.isClientSide) {
            ServerPlayer player = (ServerPlayer) user;

            // TODO: Uncomment once systems are ported
            /*
            // already has the power
            if (PowerManager.getPower(player) != null) {
                player.displayClientMessage(
                        Component.translatable("message.loopypowers.vestige.already_has_power").withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }

            // start ritual
            RitualManager.startRitual(player, RitualManager.RitualType.SPACE_VESTIGE);
            */

            // consume
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResultHolder.success(stack);
    }
}