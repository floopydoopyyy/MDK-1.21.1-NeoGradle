package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModRegistries;

/** Revives the player from a would-be death, inflicting a random curse, then consumes itself (CLAUDE.md section 3). */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class ExecutionersCoinEventHandler {
    private ExecutionersCoinEventHandler() {}

    @SubscribeEvent
    static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack coin = findCoin(player);
        if (coin.isEmpty()) {
            return;
        }

        event.setCanceled(true);
        coin.shrink(1);
        player.setHealth(1.0F);

        List<Holder.Reference<Effect>> curses = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().category() == EffectCategory.CURSE)
                .toList();
        if (!curses.isEmpty()) {
            Holder.Reference<Effect> curse = curses.get(player.getRandom().nextInt(curses.size()));
            EffectManager.apply(player, curse, 45 * 60 * 20, null);
        }
        player.displayClientMessage(Component.literal("The Executioner's Coin spares you — at a price."), false);
    }

    private static ItemStack findCoin(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(WitchModItems.EXECUTIONERS_COIN.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
