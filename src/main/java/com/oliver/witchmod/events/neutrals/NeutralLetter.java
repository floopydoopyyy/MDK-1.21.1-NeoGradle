package com.oliver.witchmod.events.neutrals;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.EventCategory;

/** A letter turns up in your inventory. No return address. */
public final class NeutralLetter extends BewitchmentEvent {
    public NeutralLetter() {
        super(EventCategory.NEUTRAL);
    }

    @Override
    public void start(ServerLevel level, @Nullable ServerPlayer initiator, int durationTicks) {
        if (initiator == null) {
            return;
        }
        ItemStack letter = new ItemStack(Items.WRITABLE_BOOK);
        letter.set(DataComponents.WRITABLE_BOOK_CONTENT,
                new WritableBookContent(List.of(Filterable.passThrough("Someone out there is thinking about you. That's all this says."))));
        if (!initiator.getInventory().add(letter)) {
            initiator.drop(letter, false);
        }
    }
}
