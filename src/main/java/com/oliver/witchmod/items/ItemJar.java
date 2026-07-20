package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.data.ActiveEffectInstance;
import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * Two-player item: right-click another player to capture an active curse off them, right-click any
 * player again to release it back out (CLAUDE.md section 3). Jar holds 1 capture and breaks after
 * releasing; Cursed Jar (via {@code maxCaptured}) holds a few and only breaks once emptied.
 */
public final class ItemJar extends Item {
    private final int maxCaptured;

    public ItemJar(Properties properties, int maxCaptured) {
        super(properties);
        this.maxCaptured = maxCaptured;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand hand) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer user) || !(interactionTarget instanceof ServerPlayer target)) {
            return InteractionResult.PASS;
        }

        List<CapturedEffect> captured = new ArrayList<>(stack.getOrDefault(WitchModDataComponents.CAPTURED_EFFECTS, List.of()));

        if (captured.size() < maxCaptured && target != user) {
            return capture(stack, user, target, captured);
        }
        if (!captured.isEmpty()) {
            return release(stack, user, target, captured);
        }
        user.displayClientMessage(Component.literal("This jar is empty and can't hold anything more from yourself."), true);
        return InteractionResult.FAIL;
    }

    private InteractionResult capture(ItemStack stack, ServerPlayer user, ServerPlayer target, List<CapturedEffect> captured) {
        ActiveEffects active = target.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        ResourceLocation curseId = active == null ? null : active.activeIds().stream()
                .filter(id -> WitchModRegistries.EFFECT_REGISTRY.getOptional(id).map(e -> e.category() == EffectCategory.CURSE).orElse(false))
                .findFirst().orElse(null);
        if (curseId == null) {
            user.displayClientMessage(Component.literal(target.getName().getString() + " has no active curse to capture."), true);
            return InteractionResult.FAIL;
        }

        ActiveEffectInstance instance = active.get(curseId).orElseThrow();
        Holder.Reference<Effect> curse = WitchModRegistries.EFFECT_REGISTRY.getHolderOrThrow(
                ResourceKey.create(WitchModRegistries.EFFECT_REGISTRY_KEY, curseId));
        EffectManager.remove(target, curse);

        captured.add(new CapturedEffect(curseId, instance.remainingTicks()));
        stack.set(WitchModDataComponents.CAPTURED_EFFECTS, List.copyOf(captured));
        user.displayClientMessage(Component.literal("Captured " + curseId.getPath() + " from " + target.getName().getString() + "."), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult release(ItemStack stack, ServerPlayer user, ServerPlayer target, List<CapturedEffect> captured) {
        List<CapturedEffect> remaining = new ArrayList<>(captured);
        CapturedEffect toRelease = remaining.remove(0);

        Holder.Reference<Effect> curse = WitchModRegistries.EFFECT_REGISTRY.getHolderOrThrow(
                ResourceKey.create(WitchModRegistries.EFFECT_REGISTRY_KEY, toRelease.effectId()));
        EffectManager.apply(target, curse, toRelease.remainingTicks(), null);
        user.displayClientMessage(Component.literal("Released " + toRelease.effectId().getPath() + " onto " + target.getName().getString() + "."), true);

        if (remaining.isEmpty()) {
            stack.shrink(1);
        } else {
            stack.set(WitchModDataComponents.CAPTURED_EFFECTS, List.copyOf(remaining));
        }
        return InteractionResult.SUCCESS;
    }
}
