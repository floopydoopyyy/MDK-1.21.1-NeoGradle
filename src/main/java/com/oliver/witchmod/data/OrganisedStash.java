package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Organised blessing's 9 extra inventory slots (Phase D), backed by the serialized
 * {@link WitchModAttachments#ORGANISED_ITEMS} attachment. Opened as a plain vanilla 1-row chest menu — no
 * custom Menu/Screen needed — via a {@link SimpleContainer} that writes itself back to the attachment when
 * the menu closes.
 */
public final class OrganisedStash {
    public static final int SIZE = 9;

    private OrganisedStash() {}

    /** Builds a container seeded from the player's stored slots that saves back to the attachment on close. */
    public static SimpleContainer openContainer(ServerPlayer owner) {
        SimpleContainer container = new SimpleContainer(SIZE) {
            @Override
            public void stopOpen(Player player) {
                super.stopOpen(player);
                save(owner, this);
            }
        };
        List<ItemStack> stored = owner.getData(WitchModAttachments.ORGANISED_ITEMS);
        for (int i = 0; i < SIZE && i < stored.size(); i++) {
            container.setItem(i, stored.get(i).copy());
        }
        return container;
    }

    private static void save(ServerPlayer owner, Container container) {
        List<ItemStack> items = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            items.add(container.getItem(i).copy());
        }
        owner.setData(WitchModAttachments.ORGANISED_ITEMS, items);
    }

    /** Drops every stored item and clears the stash — called when the blessing expires (drop-on-expire). */
    public static void dropAll(ServerPlayer owner) {
        for (ItemStack stack : owner.getData(WitchModAttachments.ORGANISED_ITEMS)) {
            if (!stack.isEmpty()) {
                owner.drop(stack.copy(), false);
            }
        }
        owner.setData(WitchModAttachments.ORGANISED_ITEMS, List.of());
    }
}
