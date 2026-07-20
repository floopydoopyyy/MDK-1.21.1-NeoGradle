package com.oliver.witchmod.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.blocks.BewitchingTableBlockEntity;
import com.oliver.witchmod.blocks.BewitchingTableRitual;
import com.oliver.witchmod.blocks.WitchModBlocks;

/**
 * The real Bewitching Table screen's menu (CLAUDE.md section 2.1/9), replacing the Phase 4 placeholder's
 * direct block-right-click interaction. Triangle layout: top = Player Essence, lower-left = Cursed
 * Essence, lower-right = Sacrificial Item; Modifier slot to the left of the triangle.
 *
 * <p>Casting is a vanilla container button click (the same built-in mechanism the Enchanting Table, Loom,
 * and Stonecutter use for server-authoritative GUI actions) rather than a hand-rolled payload — no custom
 * network code needed since NeoForge/vanilla already provide this exact hook for "GUI button needs
 * server-side logic."
 */
public final class BewitchingTableMenu extends AbstractContainerMenu {
    public static final int BUTTON_CAST = 0;

    private static final int RITUAL_SLOT_COUNT = BewitchingTableBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = RITUAL_SLOT_COUNT + 36;

    private final Container table;
    private final ContainerLevelAccess access;

    /** Client-side reconstruction (CLAUDE.md's "payloading should be utilised" note — see {@link WitchModMenus}). */
    public BewitchingTableMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        this(windowId, playerInventory, new SimpleContainer(RITUAL_SLOT_COUNT),
                ContainerLevelAccess.create(playerInventory.player.level(), pos));
    }

    /** Server-side, opened from {@link com.oliver.witchmod.blocks.BewitchingTableBlock}. */
    public BewitchingTableMenu(int windowId, Inventory playerInventory, BewitchingTableBlockEntity table) {
        this(windowId, playerInventory, table, ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()));
    }

    private BewitchingTableMenu(int windowId, Inventory playerInventory, Container table, ContainerLevelAccess access) {
        super(WitchModMenus.BEWITCHING_TABLE.get(), windowId);
        this.table = table;
        this.access = access;

        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE, 80, 17, RitualSlot.Kind.PLAYER_ESSENCE));
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE, 62, 53, RitualSlot.Kind.CURSED_ESSENCE));
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM, 98, 53, RitualSlot.Kind.SACRIFICIAL_ITEM));
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_MODIFIER, 26, 35, RitualSlot.Kind.MODIFIER));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 119 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 177));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, WitchModBlocks.BEWITCHING_TABLE.get());
    }

    /**
     * The Cast button. Runs for real only on the server call (a genuine {@link ServerPlayer} arriving via
     * {@code ServerboundContainerButtonClickPacket}) — the client's own immediate call to this method
     * (see {@code BewitchingTableScreen}) always sees a {@code LocalPlayer} here and no-ops, so the ritual
     * never double-executes or runs client-side.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CAST || !(player instanceof ServerPlayer serverPlayer) || !(table instanceof BewitchingTableBlockEntity real)) {
            return false;
        }
        access.execute((level, pos) -> BewitchingTableRitual.cast((ServerLevel) level, pos, real, serverPlayer));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < RITUAL_SLOT_COUNT) {
                if (!moveItemStackTo(stack, RITUAL_SLOT_COUNT, PLAYER_INV_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int target = resolveRitualSlot(stack);
                if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    private int resolveRitualSlot(ItemStack stack) {
        for (int i = 0; i < RITUAL_SLOT_COUNT; i++) {
            if (this.slots.get(i).mayPlace(stack)) {
                return i;
            }
        }
        return -1;
    }
}
