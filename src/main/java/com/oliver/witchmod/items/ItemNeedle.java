package com.oliver.witchmod.items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * Right-click on a bound Voodoo Doll in inventory to directly damage the bound target, consuming the
 * Doll's durability rather than the Needle's own (CLAUDE.md section 3).
 */
public final class ItemNeedle extends Item {
    private static final float DAMAGE_AMOUNT = 2.0F;

    public ItemNeedle(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack needle = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(needle);
        }

        ItemStack doll = findBoundDoll(player);
        if (doll.isEmpty()) {
            player.displayClientMessage(Component.literal("You need a bound Voodoo Doll in your inventory."), true);
            return InteractionResultHolder.fail(needle);
        }

        PlayerEssenceData bound = doll.get(WitchModDataComponents.BOUND_PLAYER);
        ServerPlayer caster = (ServerPlayer) player;
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            caster.displayClientMessage(Component.literal(bound.playerName() + " isn't online right now."), true);
            return InteractionResultHolder.fail(needle);
        }

        target.hurt(((ServerLevel) level).damageSources().magic(), DAMAGE_AMOUNT);
        doll.hurtAndBreak(1, (ServerLevel) level, caster, item -> caster.displayClientMessage(Component.literal("The doll crumbles apart."), true));
        return InteractionResultHolder.success(needle);
    }

    private static ItemStack findBoundDoll(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ItemVoodooDoll && stack.has(WitchModDataComponents.BOUND_PLAYER)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
