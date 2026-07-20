package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.data.ActiveEffectInstance;
import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;

/** Forwards your own active curses onto another player when clicked (CLAUDE.md section 3). Single-use. */
public final class ItemEffigy extends Item {
    public ItemEffigy(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand hand) {
        if (player.level().isClientSide()
                || !(player instanceof ServerPlayer caster)
                || !(interactionTarget instanceof ServerPlayer target)
                || caster == target) {
            return InteractionResult.PASS;
        }

        ActiveEffects active = caster.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            caster.displayClientMessage(Component.literal("You have no curses to forward."), true);
            return InteractionResult.FAIL;
        }

        List<ResourceLocation> curseIds = new ArrayList<>();
        for (ResourceLocation id : active.activeIds()) {
            WitchModRegistries.EFFECT_REGISTRY.getOptional(id).ifPresent(effect -> {
                if (effect.category() == com.oliver.witchmod.data.EffectCategory.CURSE) {
                    curseIds.add(id);
                }
            });
        }
        if (curseIds.isEmpty()) {
            caster.displayClientMessage(Component.literal("You have no curses to forward."), true);
            return InteractionResult.FAIL;
        }

        for (ResourceLocation id : curseIds) {
            ActiveEffectInstance instance = active.get(id).orElseThrow();
            Holder.Reference<Effect> effect = WitchModRegistries.EFFECT_REGISTRY.getHolderOrThrow(
                    net.minecraft.resources.ResourceKey.create(WitchModRegistries.EFFECT_REGISTRY_KEY, id));
            EffectManager.remove(caster, effect);
            EffectManager.apply(target, effect, instance.remainingTicks(), caster);
        }

        stack.shrink(1);
        caster.displayClientMessage(Component.literal("Your curses now belong to " + target.getName().getString() + "."), true);
        return InteractionResult.SUCCESS;
    }
}
