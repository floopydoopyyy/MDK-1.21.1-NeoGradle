package com.oliver.witchmod.ui;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.SacrificialItems;
import com.oliver.witchmod.items.WitchModItems;

/**
 * One of the Table's 4 ritual slots (CLAUDE.md section 2.1), restricted to the item type that slot
 * accepts — mirrors the item-identity checks the Phase 4 placeholder ({@code BewitchingTableBlock})
 * used directly, now centralized here since the GUI is the only way to fill these slots.
 */
final class RitualSlot extends Slot {
    enum Kind {
        PLAYER_ESSENCE,
        CURSED_ESSENCE,
        SACRIFICIAL_ITEM,
        MODIFIER
    }

    private final Kind kind;

    RitualSlot(Container container, int index, int x, int y, Kind kind) {
        super(container, index, x, y);
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return switch (kind) {
            case PLAYER_ESSENCE -> stack.getItem() == WitchModItems.PLAYER_ESSENCE.get();
            case CURSED_ESSENCE -> stack.getItem() == WitchModItems.CURSED_ESSENCE.get();
            // Redstone Dust is accepted too: it's the "random attachment" table mechanic (master-spec
            // Section 3), not a normal effect selector.
            case SACRIFICIAL_ITEM -> stack.is(Items.REDSTONE) || SacrificialItems.findEffect(stack.getItem()).isPresent();
            case MODIFIER -> ModifierItems.findModifier(stack.getItem(), WitchModItems.RECOVERY_COMPASS.get()).isPresent();
        };
    }

    /** Only Cursed Essence stacks (essenceSpent = stack count, CLAUDE.md section 5.7) — the other 3 are single-item selectors. */
    @Override
    public int getMaxStackSize() {
        return kind == Kind.CURSED_ESSENCE ? 64 : 1;
    }
}
