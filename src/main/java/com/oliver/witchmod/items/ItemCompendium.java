package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EventCategory;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * Discovery log / tutorial / item guide (CLAUDE.md section 3, section 9 item 2). Same zero-networking
 * pattern as {@link com.oliver.witchmod.blocks.LedgerBlock}: using this hands the player a fresh Written
 * Book, rebuilt from scratch every use so undiscovered curses/blessings/events show a vague "rumour"
 * blurb instead of full details (section 9), via {@link DiscoveryManager}.
 *
 * <p>Vanilla's book screen only supports linear page-flipping, not real clickable tabs — true "bookmark"
 * navigation (section 9) would need a custom Screen plus networking to ship the discovery/registry data
 * to the client. Simplified here to a fixed page order across the same 6 sections instead; the section
 * headers on every page double as the bookmark labels. Revisit with a real tabbed Screen in a later UI
 * pass if that's wanted.
 */
public final class ItemCompendium extends Item {
    private static final int ITEMS_PER_PAGE = 4;
    private static final int EFFECTS_PER_PAGE = 4;
    private static final int EVENTS_PER_PAGE = 4;
    private static final int MODIFIERS_PER_PAGE = 3;

    private static final List<String[]> ITEMS_AND_BLOCKS = List.of(
            new String[]{"Cursed Essence", "Currency for the Table."},
            new String[]{"Player Essence", "Targets a player for the Table."},
            new String[]{"Compendium", "This book."},
            new String[]{"Voodoo Doll", "Forwards curses to a bound player."},
            new String[]{"Needle", "Damages a Doll's bound target."},
            new String[]{"Ward", "Deflects incoming curses."},
            new String[]{"Scrying Mirror", "Reveals your own active effects."},
            new String[]{"Effigy", "Forwards your curses onto another."},
            new String[]{"Cursed Coin", "Gambles a random curse or blessing."},
            new String[]{"Blessed Coin", "Grants a random blessing."},
            new String[]{"Executioner's Coin", "Revives you once, cursed."},
            new String[]{"Jar", "Captures a curse off a player."},
            new String[]{"Cursed Jar", "Stores several curses."},
            new String[]{"Recovery Compass", "Table modifier item."},
            new String[]{"Bewitching Table", "Cast curses and blessings."},
            new String[]{"Block of Cursed Essence", "Stores essence; Global bank currency."},
            new String[]{"Warding Totem", "Area shield against effects."},
            new String[]{"Purifying Water", "Bathe to burn down active timers."},
            new String[]{"Ledger", "Read-only log of all activity."}
    );

    private static final List<String[]> BACKFIRES = List.of(
            new String[]{"Mirror", "A failed curse redirects onto its caster instead."},
            new String[]{"Essence Backlash", "Damage plus a loss of essence."},
            new String[]{"Table Tantrum", "Your Table locks itself on cooldown."},
            new String[]{"Essence Leak", "Essence spills out nearby for others to grab."},
            new String[]{"Marked", "You're temporarily cheaper for others to curse."}
    );

    public ItemCompendium(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ItemStack book = buildCompendiumBook(serverPlayer);
            if (!serverPlayer.getInventory().add(book)) {
                serverPlayer.drop(book, false);
            }
            serverPlayer.displayClientMessage(Component.literal("You've been handed a copy of the Compendium."), true);
        }
        return InteractionResultHolder.success(stack);
    }

    private static ItemStack buildCompendiumBook(ServerPlayer player) {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(tocPage()));
        pages.addAll(tutorialPages());
        pages.addAll(itemsAndBlocksPages());
        pages.addAll(effectPages(player));
        pages.addAll(eventPages(player));
        pages.addAll(backfirePages());
        pages.addAll(modifierPages());

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("The Compendium"), "The Compendium", 0, pages, true));
        return book;
    }

    private static Component tocPage() {
        return Component.literal("The Compendium").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append("\n\n")
                .append(Component.literal(
                        "I. Tutorial\nII. Items & Blocks\nIII. Rumours: Curses & Blessings\nIV. Rumours: Events\nV. Rumours: Backfires\nVI. Modifiers"
                ).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static List<Filterable<Component>> tutorialPages() {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(section("I. Tutorial").append("\n\n")
                .append("Spend Cursed Essence at a Bewitching Table to place a curse or blessing on another player, "
                        + "using a bottle of their Player Essence to target them and a Sacrificial Item to pick the effect.")));
        pages.add(Filterable.passThrough(Component.literal(
                "Effects are hidden - a cursed or blessed player only knows something happened, not what. Each "
                        + "effect unlocks a page in this book the first time it happens to you, or the moment you successfully cast it.")));
        pages.add(Filterable.passThrough(Component.literal(
                "A Ward necklace deflects curses back at their sender. A Warding Totem shields everyone in its "
                        + "radius silently, with no feedback on what it blocked. Every attempt - landed or blocked - is written to the Ledger.")));
        return pages;
    }

    private static List<Filterable<Component>> itemsAndBlocksPages() {
        return paginate(ITEMS_AND_BLOCKS, ITEMS_PER_PAGE, "II. Items & Blocks");
    }

    private static List<Filterable<Component>> effectPages(ServerPlayer player) {
        List<Holder.Reference<Effect>> sorted = WitchModRegistries.EFFECT_REGISTRY.holders()
                .sorted(Comparator.<Holder.Reference<Effect>>comparingInt(h -> h.value().category().ordinal())
                        .thenComparing(h -> h.key().location().getPath()))
                .toList();

        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i += EFFECTS_PER_PAGE) {
            List<Holder.Reference<Effect>> chunk = sorted.subList(i, Math.min(i + EFFECTS_PER_PAGE, sorted.size()));
            MutableComponent page = section("III. Rumours: Curses & Blessings").append("\n\n");
            for (int j = 0; j < chunk.size(); j++) {
                if (j > 0) page.append("\n\n");
                page.append(effectEntry(player, chunk.get(j)));
            }
            pages.add(Filterable.passThrough(page));
        }
        return pages;
    }

    private static Component effectEntry(ServerPlayer player, Holder.Reference<Effect> holder) {
        Effect effect = holder.value();
        String name = titleCase(holder.key().location().getPath());
        String category = effect.category() == EffectCategory.CURSE ? "Curse" : "Blessing";
        boolean discovered = DiscoveryManager.hasDiscoveredEffect(player, holder.key().location());

        MutableComponent entry = Component.literal(name).withStyle(ChatFormatting.BOLD)
                .append(Component.literal(" (" + category + ")").withStyle(ChatFormatting.DARK_GRAY))
                .append("\n");
        if (discovered) {
            entry.append(Component.literal("Tier: " + titleCase(effect.tier().name()) + ", Cost: " + effect.baseCost()
                    + ". Sacrifice: " + titleCase(itemPath(effect.sacrificialItem())) + ".").withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            entry.append(Component.literal("Rumours only - its true effects remain a mystery to you.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return entry;
    }

    private static List<Filterable<Component>> eventPages(ServerPlayer player) {
        List<Holder.Reference<BewitchmentEvent>> sorted = WitchModRegistries.EVENT_REGISTRY.holders()
                .sorted(Comparator.<Holder.Reference<BewitchmentEvent>>comparingInt(h -> h.value().category().ordinal())
                        .thenComparing(h -> h.key().location().getPath()))
                .toList();

        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i += EVENTS_PER_PAGE) {
            List<Holder.Reference<BewitchmentEvent>> chunk = sorted.subList(i, Math.min(i + EVENTS_PER_PAGE, sorted.size()));
            MutableComponent page = section("IV. Rumours: Events").append("\n\n");
            for (int j = 0; j < chunk.size(); j++) {
                if (j > 0) page.append("\n\n");
                page.append(eventEntry(player, chunk.get(j)));
            }
            pages.add(Filterable.passThrough(page));
        }
        return pages;
    }

    private static Component eventEntry(ServerPlayer player, Holder.Reference<BewitchmentEvent> holder) {
        BewitchmentEvent event = holder.value();
        String name = titleCase(holder.key().location().getPath());
        String category = event.category() == EventCategory.NEUTRAL ? "Neutral" : "Global";
        boolean discovered = DiscoveryManager.hasDiscoveredEvent(player, holder.key().location());

        return Component.literal(name).withStyle(ChatFormatting.BOLD)
                .append(Component.literal(" (" + category + ")").withStyle(ChatFormatting.DARK_GRAY))
                .append("\n")
                .append(discovered
                        ? Component.literal("You've witnessed this happen.").withStyle(ChatFormatting.DARK_PURPLE)
                        : Component.literal("Rumours only - unwitnessed so far.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static List<Filterable<Component>> backfirePages() {
        return paginate(BACKFIRES, BACKFIRES.size(), "V. Rumours: Backfires");
    }

    private static List<Filterable<Component>> modifierPages() {
        Modifier[] modifiers = Modifier.values();
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 0; i < modifiers.length; i += MODIFIERS_PER_PAGE) {
            int end = Math.min(i + MODIFIERS_PER_PAGE, modifiers.length);
            MutableComponent page = section("VI. Modifiers").append("\n\n");
            for (int j = i; j < end; j++) {
                if (j > i) page.append("\n\n");
                page.append(modifierEntry(modifiers[j]));
            }
            pages.add(Filterable.passThrough(page));
        }
        return pages;
    }

    private static Component modifierEntry(Modifier modifier) {
        return Component.literal(titleCase(modifier.name())).withStyle(ChatFormatting.BOLD)
                .append("\n")
                .append(Component.literal(String.format("Cost %+d%%, Duration %+d%%, Success %+d%%, Backfire %+d%%",
                        modifier.costDeltaPercent(), modifier.durationDeltaPercent(),
                        modifier.successDeltaPercent(), modifier.backfireDeltaPercent())).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static List<Filterable<Component>> paginate(List<String[]> entries, int perPage, String heading) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += perPage) {
            List<String[]> chunk = entries.subList(i, Math.min(i + perPage, entries.size()));
            MutableComponent page = section(heading).append("\n\n");
            for (int j = 0; j < chunk.size(); j++) {
                if (j > 0) page.append("\n\n");
                page.append(Component.literal(chunk.get(j)[0]).withStyle(ChatFormatting.BOLD))
                        .append("\n")
                        .append(Component.literal(chunk.get(j)[1]).withStyle(ChatFormatting.DARK_GRAY));
            }
            pages.add(Filterable.passThrough(page));
        }
        return pages;
    }

    private static MutableComponent section(String title) {
        return Component.literal(title).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
    }

    private static String itemPath(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    private static String titleCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }
}
