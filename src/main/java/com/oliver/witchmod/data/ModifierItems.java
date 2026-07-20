package com.oliver.witchmod.data;

import java.util.Optional;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Reverse lookup from a Modifier item (CLAUDE.md section 4.6/section 6.4 note) back to the {@link Modifier}
 * it represents. Most modifiers reuse an existing vanilla item directly; Recovery Compass is the one
 * exception (no vanilla equivalent), so its item is passed in rather than referenced here, keeping this
 * class free of a dependency on the items package.
 */
public final class ModifierItems {
    private ModifierItems() {}

    public static Optional<Modifier> findModifier(Item item, Item recoveryCompassItem) {
        if (item == recoveryCompassItem) {
            return Optional.of(Modifier.RECOVERY_COMPASS);
        }
        if (item == Items.CLOCK) {
            return Optional.of(Modifier.CLOCK);
        }
        if (item == Items.COMPASS) {
            return Optional.of(Modifier.COMPASS);
        }
        if (item == Items.NETHER_STAR) {
            return Optional.of(Modifier.NETHERSTAR);
        }
        if (item == Items.PRISMARINE_SHARD) {
            return Optional.of(Modifier.PRISMARINE_SHARD);
        }
        if (item == Items.QUARTZ) {
            return Optional.of(Modifier.QUARTZ);
        }
        if (item == Items.DRAGON_BREATH) {
            return Optional.of(Modifier.DRAGONS_BREATH);
        }
        if (item == Items.NETHERITE_INGOT) {
            return Optional.of(Modifier.NETHERITE_INGOT);
        }
        if (item == Items.INK_SAC) {
            return Optional.of(Modifier.INK_SAC);
        }
        if (item == Items.GLOW_INK_SAC) {
            return Optional.of(Modifier.GLOW_INK_SAC);
        }
        if (item == Items.RABBIT_FOOT) {
            return Optional.of(Modifier.RABBITS_FOOT);
        }
        if (item == Items.ECHO_SHARD) {
            return Optional.of(Modifier.ECHO_SHARD);
        }
        if (item == Items.GOAT_HORN) {
            return Optional.of(Modifier.GOAT_HORN);
        }
        if (item == Items.SUGAR) {
            return Optional.of(Modifier.SUGAR);
        }
        if (item == Items.HONEYCOMB) {
            return Optional.of(Modifier.HONEYCOMB);
        }
        // Gunpowder / Redstone / Milk Bucket are intentionally NOT modifiers (master-spec Section 9) — they
        // serve as sacrificial items / the Redstone-Dust table mechanic instead.
        return Optional.empty();
    }
}
