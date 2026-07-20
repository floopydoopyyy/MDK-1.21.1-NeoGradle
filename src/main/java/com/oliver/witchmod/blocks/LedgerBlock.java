package com.oliver.witchmod.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.oliver.witchmod.data.LedgerLog;

/**
 * Read-only book UI accessed via a lectern-style block (CLAUDE.md section 2.3): right-click hands the
 * player a fresh Written Book snapshotting {@link LedgerLog}'s current contents. Reading it uses vanilla's
 * own book screen entirely — no custom Screen or networking code, which is why this is the simplest of
 * the 3 Phase 5 screens and was built first, per the doc's own recommendation. "Never player-editable"
 * (section 9) falls out for free: written books can't be edited once written.
 *
 * <p>Not a literal {@code LecternBlock} subclass — that brings book-holding/redstone-signal machinery this
 * doesn't need. "Lectern-style" here just means the same read-only-book interaction shape.
 */
public final class LedgerBlock extends Block {
    private static final int ENTRIES_PER_PAGE = 6;

    public LedgerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack book = buildLedgerBook();
        if (!serverPlayer.getInventory().add(book)) {
            serverPlayer.drop(book, false);
        }
        serverPlayer.displayClientMessage(Component.literal("You've been handed a copy of the Ledger. Read it to see recent activity."), true);
        return InteractionResult.SUCCESS;
    }

    private static ItemStack buildLedgerBook() {
        List<LedgerLog.Entry> entries = new ArrayList<>(LedgerLog.recent());
        Collections.reverse(entries); // newest first

        List<Filterable<Component>> pages = new ArrayList<>();
        if (entries.isEmpty()) {
            pages.add(Filterable.passThrough(Component.literal("Nothing's happened yet.").withStyle(ChatFormatting.GRAY)));
        } else {
            for (int i = 0; i < entries.size(); i += ENTRIES_PER_PAGE) {
                List<LedgerLog.Entry> pageEntries = entries.subList(i, Math.min(i + ENTRIES_PER_PAGE, entries.size()));
                pages.add(Filterable.passThrough(formatPage(pageEntries)));
            }
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("The Ledger"), "The Ledger", 0, pages, true));
        return book;
    }

    private static Component formatPage(List<LedgerLog.Entry> pageEntries) {
        MutableComponent page = Component.empty();
        for (int i = 0; i < pageEntries.size(); i++) {
            if (i > 0) {
                page.append("\n\n");
            }
            page.append(formatEntry(pageEntries.get(i)));
        }
        return page;
    }

    private static Component formatEntry(LedgerLog.Entry entry) {
        MutableComponent line = Component.literal(entry.casterName().orElse("(system)")).withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(" -> ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(entry.targetName()).withStyle(ChatFormatting.DARK_PURPLE))
                .append("\n")
                .append(Component.literal(entry.effectId().getPath()).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(" - " + entry.result()).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
