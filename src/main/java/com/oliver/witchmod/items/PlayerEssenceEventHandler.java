package com.oliver.witchmod.items;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * Player Essence's acquisition method IS the interaction (CLAUDE.md section 3: "Bottle used on a player
 * or their bed") rather than a crafting-table recipe, so this listens on the vanilla Glass Bottle instead
 * of needing a custom bottle item.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PlayerEssenceEventHandler {
    private PlayerEssenceEventHandler() {}

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(Items.GLASS_BOTTLE) || event.getLevel().isClientSide()) {
            return;
        }
        Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer user) || !(target instanceof ServerPlayer targetPlayer)) {
            return;
        }

        bindAndGive(user, targetPlayer.getUUID(), targetPlayer.getName().getString());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Items.GLASS_BOTTLE) || !(event.getEntity() instanceof ServerPlayer user)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        if (!level.getBlockState(event.getPos()).is(BlockTags.BEDS)) {
            return;
        }

        ServerPlayer bedOwner = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.getRespawnDimension() == level.dimension())
                .filter(p -> p.getRespawnPosition() != null && p.getRespawnPosition().closerThan(event.getPos(), 2))
                .findFirst()
                .orElse(null);

        if (bedOwner == null) {
            user.displayClientMessage(Component.literal("This bed doesn't seem to belong to anyone online."), true);
            return;
        }

        bindAndGive(user, bedOwner.getUUID(), bedOwner.getName().getString());
        event.setCanceled(true);
    }

    private static void bindAndGive(ServerPlayer user, UUID boundPlayerId, String boundPlayerName) {
        ItemStack essence = new ItemStack(WitchModItems.PLAYER_ESSENCE.get());
        essence.set(WitchModDataComponents.BOUND_PLAYER, new PlayerEssenceData(boundPlayerId, boundPlayerName));

        user.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
        if (!user.getInventory().add(essence)) {
            user.drop(essence, false);
        }
        user.displayClientMessage(Component.literal("Bottled a trace of " + boundPlayerName + "."), true);
    }
}
