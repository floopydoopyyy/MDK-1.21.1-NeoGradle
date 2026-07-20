package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** Nothing in your hands wears out while this lasts. */
public final class BlessingToolsDontUseDurability extends Effect {
    private static final int INTERVAL_TICKS = 20;

    public BlessingToolsDontUseDurability() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.NETHERITE_SCRAP);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            for (ItemStack stack : target.getInventory().items) {
                if (!stack.isEmpty() && stack.isDamageableItem() && !stack.has(DataComponents.UNBREAKABLE)) {
                    stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
                }
            }
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        for (ItemStack stack : target.getInventory().items) {
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.remove(DataComponents.UNBREAKABLE);
            }
        }
    }
}
