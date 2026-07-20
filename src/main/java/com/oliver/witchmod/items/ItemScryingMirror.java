package com.oliver.witchmod.items;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.ActiveEffectInstance;
import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.WitchModAttachments;

/** Reveals your own active curses/blessings (CLAUDE.md section 3) — text-only for now, no UI until Phase 5. */
public final class ItemScryingMirror extends Item {
    public ItemScryingMirror(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        ActiveEffects active = serverPlayer.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            serverPlayer.displayClientMessage(Component.literal("The mirror shows nothing unusual."), false);
            return InteractionResultHolder.success(stack);
        }

        serverPlayer.displayClientMessage(Component.literal("The mirror reveals:"), false);
        for (ResourceLocation id : active.activeIds()) {
            active.get(id).ifPresent(instance -> serverPlayer.displayClientMessage(
                    Component.literal(" - " + id.getPath() + " (" + ticksToSeconds(instance) + "s left)"), false));
        }
        return InteractionResultHolder.success(stack);
    }

    private static long ticksToSeconds(ActiveEffectInstance instance) {
        return instance.remainingTicks() / 20L;
    }
}
