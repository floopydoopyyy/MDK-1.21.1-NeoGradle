package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.OrganisedStash;

/**
 * A place for everything (master-spec Organised — a CUSTOM UI blessing, Phase D). Grants 9 extra inventory
 * slots (a personal stash opened with {@code /bewitch organised}, backed by {@link OrganisedStash}), whose
 * contents drop when the blessing expires. As a bonus it also auto-consolidates matching stacks in your
 * main inventory.
 *
 * <p>PROTOTYPE: the extra slots open via a command rather than being injected as a literal extra row into
 * the vanilla inventory screen (which needs a mixin); a keybind for survival access is the intended
 * follow-up (Human Action Items).
 */
public final class BlessingOrganised extends Effect {
    private static final int INTERVAL_TICKS = 100;

    public BlessingOrganised() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.SHULKER_SHELL);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        OrganisedStash.dropAll(target); // ORGANISED_DROP_ON_EXPIRE
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        NonNullList<ItemStack> items = target.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack from = items.get(i);
            if (from.isEmpty() || from.getCount() >= from.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < items.size(); j++) {
                ItemStack into = items.get(j);
                if (into.isEmpty() || !ItemStack.isSameItemSameComponents(from, into) || into.getCount() >= into.getMaxStackSize()) {
                    continue;
                }
                int space = into.getMaxStackSize() - into.getCount();
                int moved = Math.min(space, from.getCount());
                into.grow(moved);
                from.shrink(moved);
                if (from.isEmpty()) {
                    break;
                }
            }
        }
    }
}
