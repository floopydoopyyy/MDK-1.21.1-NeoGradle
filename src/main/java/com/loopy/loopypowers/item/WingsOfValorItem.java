package com.loopy.loopypowers.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class WingsOfValorItem extends ElytraItem {

    public WingsOfValorItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        // Gray = flavour text
        tooltip.add(Component.translatable("item.loopypowers.wings_of_valor.tooltip.flavor")
                .withStyle(ChatFormatting.GRAY));
        // Light red = warning
        tooltip.add(Component.translatable("item.loopypowers.wings_of_valor.tooltip.warning")
                .withStyle(ChatFormatting.RED));

        super.appendHoverText(stack, context, tooltip, type);
    }

    // ============================================================
    // NEOFORGE ELYTRA
    // ============================================================
    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return true; // this is so good why doesnt fabric have this
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;

        if (living.getItemBySlot(EquipmentSlot.CHEST) == stack) {
            if (stack.getDamageValue() != 0) {
                stack.setDamageValue(0);
            }
        }
    }
}