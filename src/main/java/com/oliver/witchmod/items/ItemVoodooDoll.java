package com.oliver.witchmod.items;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.LedgerLog;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * Forwards curses to a bound player instead of a fresh target (CLAUDE.md section 3). Combine with a
 * Player Essence held in the off-hand to bind it; once bound, using it forwards a random curse onto the
 * bound player and consumes 1 durability.
 */
public final class ItemVoodooDoll extends BoundPlayerItem {
    public ItemVoodooDoll(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        if (bound == null) {
            return tryBind(stack, player);
        }
        return forwardCurse(stack, (ServerPlayer) player, bound);
    }

    private InteractionResultHolder<ItemStack> tryBind(ItemStack stack, Player player) {
        ItemStack offhand = player.getOffhandItem();
        PlayerEssenceData essenceData = offhand.get(WitchModDataComponents.BOUND_PLAYER);
        if (essenceData == null) {
            player.displayClientMessage(Component.literal("This doll isn't bound to anyone yet — hold a Player Essence in your off-hand to bind it."), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.set(WitchModDataComponents.BOUND_PLAYER, essenceData);
        offhand.shrink(1);
        player.displayClientMessage(Component.literal("Bound to " + essenceData.playerName() + "."), true);
        return InteractionResultHolder.success(stack);
    }

    private InteractionResultHolder<ItemStack> forwardCurse(ItemStack stack, ServerPlayer caster, PlayerEssenceData bound) {
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            caster.displayClientMessage(Component.literal(bound.playerName() + " isn't online right now."), true);
            return InteractionResultHolder.fail(stack);
        }

        List<Holder.Reference<Effect>> curses = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().category() == EffectCategory.CURSE)
                .toList();
        if (curses.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        Holder.Reference<Effect> curse = curses.get(caster.getRandom().nextInt(curses.size()));
        int durationTicks = 45 * 60 * 20;
        EffectManager.apply(target, curse, durationTicks, caster);
        LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(), curse.key().location(), "doll_forward", ((ServerLevel) caster.level()).getGameTime());

        stack.hurtAndBreak(1, (ServerLevel) caster.level(), caster, item -> caster.displayClientMessage(Component.literal("The doll crumbles apart."), true));
        return InteractionResultHolder.success(stack);
    }
}
