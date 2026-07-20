package com.oliver.witchmod.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.blocks.BewitchingTableBlockEntity;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.ModifierCalculator;
import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.SacrificialItems;
import com.oliver.witchmod.items.WitchModItems;

/**
 * The real Bewitching Table screen (CLAUDE.md section 2.1/9) — first custom {@code Screen} in the mod
 * (Ledger and Compendium both dodged one entirely via the "hand the player a Written Book" trick). Flagged
 * in CLAUDE.md as new territory for the developer, so this is deliberately plain: no custom textures exist
 * yet (see Human Action Items), so the whole background is procedural {@code GuiGraphics.fill} rectangles
 * instead of a texture atlas — a drop-in-a-PNG upgrade path later, same as the block/item model scaffolding
 * from earlier in Phase 5.
 *
 * <p>The purple probability bar (section 2.1) is computed reactively every frame straight from the 3
 * relevant slots' current contents via the same pure {@link ModifierCalculator} functions the server uses
 * to actually resolve the cast — no extra networking needed for this, since the Effect registry is synced
 * to the client and the slot contents are already client-visible through vanilla's normal container sync.
 */
public final class BewitchingTableScreen extends AbstractContainerScreen<BewitchingTableMenu> {
    private static final int PANEL_COLOR = 0xF01B1123;
    private static final int PANEL_BORDER = 0xFF4A2E63;
    private static final int SLOT_COLOR = 0xFF2B1B3B;
    private static final int SLOT_BORDER = 0xFF8B6FB0;
    private static final int BAR_BG = 0xFF241531;
    private static final int BAR_SUCCESS = 0xFFA050E0;
    private static final int BAR_BACKFIRE = 0xFFD03050;
    private static final int TEXT_COLOR = 0xFFE0D0F0;
    private static final int BAR_X = 18;
    private static final int BAR_Y = 84;
    private static final int BAR_WIDTH = 140;
    private static final int BAR_HEIGHT = 8;
    private static final int CAST_BUTTON_X = 58;
    private static final int CAST_BUTTON_Y = 97;
    private static final int CAST_BUTTON_WIDTH = 60;
    private static final int CAST_BUTTON_HEIGHT = 16;

    public BewitchingTableScreen(BewitchingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 202;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 109;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        if (this.hoveredSlot instanceof RitualSlot ritualSlot && !ritualSlot.hasItem()) {
            guiGraphics.renderTooltip(this.font, Component.literal(labelFor(ritualSlot.kind())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT_COLOR, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_COLOR);
        guiGraphics.fill(x, y, x + imageWidth, y + 1, PANEL_BORDER);
        guiGraphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_BORDER);
        guiGraphics.fill(x, y, x + 1, y + imageHeight, PANEL_BORDER);
        guiGraphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_BORDER);

        for (Slot slot : this.menu.slots) {
            if (slot instanceof RitualSlot) {
                guiGraphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT_BORDER);
                guiGraphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_COLOR);
            }
        }

        renderProbabilityBar(guiGraphics, x, y);
        renderCastButton(guiGraphics, x, y, mouseX, mouseY);
    }

    private void renderProbabilityBar(GuiGraphics guiGraphics, int x, int y) {
        RitualSnapshot snapshot = readSnapshot();

        String label = snapshot.hasEffect
                ? String.format("Success %d%%   Backfire %d%%", Math.round(snapshot.successChance * 100), Math.round(snapshot.backfireChance * 100))
                : "Insert a Sacrificial Item";
        guiGraphics.drawCenteredString(this.font, label, x + imageWidth / 2, y + BAR_Y - 12, TEXT_COLOR);

        guiGraphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, BAR_BG);
        if (snapshot.hasEffect) {
            int successWidth = Math.round(BAR_WIDTH * snapshot.successChance);
            int backfireWidth = Math.round(BAR_WIDTH * snapshot.backfireChance);
            guiGraphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + successWidth, y + BAR_Y + BAR_HEIGHT, BAR_SUCCESS);
            guiGraphics.fill(x + BAR_X + BAR_WIDTH - backfireWidth, y + BAR_Y, x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, BAR_BACKFIRE);
        }
    }

    private void renderCastButton(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = isWithinCastButton(x, y, mouseX, mouseY);
        int color = hovered ? 0xFF6B3FA0 : 0xFF4A2E63;
        guiGraphics.fill(x + CAST_BUTTON_X, y + CAST_BUTTON_Y, x + CAST_BUTTON_X + CAST_BUTTON_WIDTH, y + CAST_BUTTON_Y + CAST_BUTTON_HEIGHT, color);
        guiGraphics.drawCenteredString(this.font, "Cast", x + CAST_BUTTON_X + CAST_BUTTON_WIDTH / 2, y + CAST_BUTTON_Y + 4, TEXT_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isWithinCastButton(this.leftPos, this.topPos, (int) mouseX, (int) mouseY)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BewitchingTableMenu.BUTTON_CAST);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isWithinCastButton(int panelX, int panelY, int mouseX, int mouseY) {
        int left = panelX + CAST_BUTTON_X;
        int top = panelY + CAST_BUTTON_Y;
        return mouseX >= left && mouseX < left + CAST_BUTTON_WIDTH && mouseY >= top && mouseY < top + CAST_BUTTON_HEIGHT;
    }

    private RitualSnapshot readSnapshot() {
        ItemStack sacrificialStack = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM).getItem();
        var maybeEffect = SacrificialItems.findEffect(sacrificialStack.getItem());
        if (maybeEffect.isEmpty()) {
            return RitualSnapshot.NONE;
        }
        Holder.Reference<Effect> effect = maybeEffect.get();

        ItemStack modifierStack = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_MODIFIER).getItem();
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem(), WitchModItems.RECOVERY_COMPASS.get()).orElse(null);

        int essenceSpent = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE).getItem().getCount();
        int adjustedCost = ModifierCalculator.applyCost(effect.value().baseCost(), modifier);
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        float backfireChance = ModifierCalculator.applyBackfireChance(
                ModifierCalculator.baseBackfireChance(essenceSpent, adjustedCost), modifier);
        return new RitualSnapshot(true, successChance, backfireChance);
    }

    private static String labelFor(RitualSlot.Kind kind) {
        return switch (kind) {
            case PLAYER_ESSENCE -> "Player Essence (target - empty = self)";
            case CURSED_ESSENCE -> "Cursed Essence (stack size = essence spent)";
            case SACRIFICIAL_ITEM -> "Sacrificial Item (selects the effect)";
            case MODIFIER -> "Modifier (optional)";
        };
    }

    private record RitualSnapshot(boolean hasEffect, float successChance, float backfireChance) {
        static final RitualSnapshot NONE = new RitualSnapshot(false, 0F, 0F);
    }
}
