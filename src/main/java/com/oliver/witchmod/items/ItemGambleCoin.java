package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * Gambles a random effect onto the user, consuming one coin. Backs both Cursed Coin (any effect) and
 * Blessed Coin (blessings only) — CLAUDE.md section 3.
 */
public final class ItemGambleCoin extends Item {
    private static final int DEFAULT_DURATION_TICKS = 45 * 60 * 20;

    private final boolean blessingsOnly;

    public ItemGambleCoin(Properties properties, boolean blessingsOnly) {
        super(properties);
        this.blessingsOnly = blessingsOnly;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        List<Holder.Reference<Effect>> pool = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> !blessingsOnly || holder.value().category() == com.oliver.witchmod.data.EffectCategory.BLESSING)
                .toList();
        if (pool.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        Holder.Reference<Effect> effect = pool.get(serverPlayer.getRandom().nextInt(pool.size()));
        EffectManager.apply(serverPlayer, effect, DEFAULT_DURATION_TICKS, null);
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }
}
