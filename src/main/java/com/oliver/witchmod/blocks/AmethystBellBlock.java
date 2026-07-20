package com.oliver.witchmod.blocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * Retextured bell; flips one of the right-clicking player's active effects for a different random one of
 * the same category, on a per-player cooldown (CLAUDE.md section 3).
 *
 * <p>The cooldown is a plain in-memory map, not persisted — acceptable for now since it only prevents
 * spam, but revisit if that matters after a server restart.
 */
public final class AmethystBellBlock extends Block {
    private static final int COOLDOWN_TICKS = 20 * 30;
    private static final Map<UUID, Long> LAST_USE_TICK = new WeakHashMap<>();

    public AmethystBellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        long now = level.getGameTime();
        Long lastUse = LAST_USE_TICK.get(serverPlayer.getUUID());
        if (lastUse != null && now - lastUse < COOLDOWN_TICKS) {
            serverPlayer.displayClientMessage(Component.literal("The bell isn't ready to ring again yet."), true);
            return InteractionResult.FAIL;
        }

        if (!flipRandomEffect(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("The bell rings, but nothing happens."), true);
            return InteractionResult.SUCCESS;
        }

        LAST_USE_TICK.put(serverPlayer.getUUID(), now);
        return InteractionResult.SUCCESS;
    }

    private boolean flipRandomEffect(ServerPlayer player) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return false;
        }

        ResourceLocation currentId = active.activeIds().stream()
                .skip(player.getRandom().nextInt(active.activeIds().size()))
                .findFirst().orElseThrow();
        Holder.Reference<Effect> current = WitchModRegistries.EFFECT_REGISTRY.getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(WitchModRegistries.EFFECT_REGISTRY_KEY, currentId));
        EffectCategory category = current.value().category();
        int remainingTicks = active.get(currentId).map(instance -> instance.remainingTicks()).orElse(20 * 60);

        List<Holder.Reference<Effect>> sameCategoryPool = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().category() == category && !holder.key().location().equals(currentId))
                .toList();
        if (sameCategoryPool.isEmpty()) {
            return false;
        }

        Holder.Reference<Effect> replacement = sameCategoryPool.get(player.getRandom().nextInt(sameCategoryPool.size()));
        EffectManager.remove(player, current);
        EffectManager.apply(player, replacement, remainingTicks, null);
        player.displayClientMessage(Component.literal("Something flips inside you..."), true);
        return true;
    }
}
