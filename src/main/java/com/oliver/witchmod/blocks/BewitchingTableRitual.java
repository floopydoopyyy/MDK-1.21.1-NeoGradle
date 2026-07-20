package com.oliver.witchmod.blocks;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EventCategory;
import com.oliver.witchmod.data.LedgerLog;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.ModifierCalculator;
import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.SacrificialItems;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.items.WitchModItems;

/**
 * Consumes the Table's 4 slots and runs the section 5.7 success/backfire formulas, applying the resulting
 * effect or one of the 3 documented failure outcomes (CLAUDE.md section 2.1): a Neutral event, a Mirror
 * backfire (curse onto the caster), or — rare/high-risk — the table exploding.
 *
 * <p>Exact roll boundaries (a Neutral getting whatever probability mass success/backfire don't cover, and
 * explosion being a rare sub-chance <em>within</em> the backfire branch) are this phase's own concrete
 * reading of a spec that didn't pin down the split; treat as a Phase 6 balancing knob, not fixed law.
 */
public final class BewitchingTableRitual {
    private static final int DEFAULT_DURATION_TICKS = 45 * 60 * 20;
    private static final float EXPLOSION_CHANCE_WITHIN_BACKFIRE = 0.2F;
    private static final float EXPLOSION_POWER = 2.0F;
    /** Redstone-Dust random pick: weight = 1 / cost^bias, so cheaper attachments are favoured (master-spec Section 3). */
    private static final double REDSTONE_RANDOM_LOW_BIAS = 1.5;

    private BewitchingTableRitual() {}

    /** Also called by {@link com.oliver.witchmod.ui.BewitchingTableMenu}'s Cast button (Phase 5's real screen). */
    public static void cast(ServerLevel level, BlockPos pos, BewitchingTableBlockEntity table, ServerPlayer caster) {
        ItemStack sacrificialStack = table.getItem(BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM);
        if (sacrificialStack.isEmpty()) {
            caster.displayClientMessage(Component.literal("The table needs a Sacrificial Item to do anything."), true);
            return;
        }

        Holder.Reference<Effect> effect;
        if (sacrificialStack.is(net.minecraft.world.item.Items.REDSTONE)) {
            // Redstone Dust = random attachment, weighted toward lower-cost ones (master-spec Section 3).
            effect = pickRandomLowBiased(caster);
        } else {
            Optional<Holder.Reference<Effect>> maybeEffect = SacrificialItems.findEffect(sacrificialStack.getItem());
            if (maybeEffect.isEmpty()) {
                caster.displayClientMessage(Component.literal("That item doesn't correspond to any curse or blessing."), true);
                return;
            }
            effect = maybeEffect.get();
        }

        ItemStack essenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE);
        int essenceSpent = essenceStack.getCount();

        ItemStack modifierStack = table.getItem(BewitchingTableBlockEntity.SLOT_MODIFIER);
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem(), WitchModItems.RECOVERY_COMPASS.get()).orElse(null);

        ItemStack playerEssenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE);
        ServerPlayer target = caster;
        if (!playerEssenceStack.isEmpty()) {
            PlayerEssenceData bound = playerEssenceStack.get(WitchModDataComponents.BOUND_PLAYER);
            ServerPlayer online = bound == null ? null : caster.getServer().getPlayerList().getPlayer(bound.playerId());
            if (online == null) {
                caster.displayClientMessage(Component.literal("That Player Essence's owner isn't online right now."), true);
                return;
            }
            target = online;
        }

        int baseCost = effectCost(effect);
        if (EffectManager.isActive(target, effect)) {
            // "Casting on an already-affected target costs more" (CLAUDE.md section 2.7).
            baseCost = Math.round(baseCost * (1 + Config.ESCALATING_COST_PERCENT.get() / 100F));
        }
        int adjustedCost = ModifierCalculator.applyCost(baseCost, modifier);
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        float backfireChance = ModifierCalculator.applyBackfireChance(
                ModifierCalculator.baseBackfireChance(essenceSpent, adjustedCost), modifier);
        if (EffectManager.activeCount(target) >= Config.MAX_ACTIVE_EFFECTS_PER_PLAYER.get()) {
            // "Exceeding the limit risks backfire" (CLAUDE.md section 2.7).
            backfireChance = Math.min(1F, backfireChance + Config.LIMIT_BACKFIRE_BOOST_PERCENT.get() / 100F);
        }
        if (!Config.BACKFIRES_ENABLED.get()) {
            // That probability mass falls through to the Neutral branch below instead.
            backfireChance = 0F;
        }
        int durationTicks = ModifierCalculator.applyDuration(DEFAULT_DURATION_TICKS, modifier);

        // Ingredients are spent regardless of outcome — that's the risk of casting.
        table.clearAll();

        ResourceLocation effectId = effect.key().location();
        float roll = caster.getRandom().nextFloat();

        if (roll < successChance) {
            EffectManager.apply(target, effect, durationTicks, caster);
            LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(), effectId, "success", level.getGameTime());
            caster.displayClientMessage(Component.literal("The ritual succeeds."), true);
            return;
        }

        if (roll < successChance + backfireChance) {
            if (caster.getRandom().nextFloat() < EXPLOSION_CHANCE_WITHIN_BACKFIRE) {
                explodeTable(level, pos, caster);
                LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, "table_exploded", level.getGameTime());
            } else {
                mirrorBackfire(caster, durationTicks);
                LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, "mirror_backfire", level.getGameTime());
            }
            return;
        }

        triggerRandomNeutral(level, caster, durationTicks);
        LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, "neutral_miss", level.getGameTime());
    }

    /** Config.EFFECT_COST_OVERRIDES (Phase 6) wins over the effect's code-defined default (section 5.1/5.2). */
    private static int effectCost(Holder.Reference<Effect> effect) {
        Integer override = Config.parsedEffectCostOverrides().get(effect.key().location().toString());
        return override != null ? override : effect.value().baseCost();
    }

    /**
     * Picks a random curse/blessing weighted by inverse cost — {@code weight = 1 / cost^BIAS} — so
     * lower-cost attachments are more likely (master-spec Section 3's Redstone-Dust mechanic).
     */
    private static Holder.Reference<Effect> pickRandomLowBiased(ServerPlayer caster) {
        List<Holder.Reference<Effect>> all = WitchModRegistries.EFFECT_REGISTRY.holders().toList();
        double totalWeight = 0.0;
        for (Holder.Reference<Effect> holder : all) {
            totalWeight += weightFor(holder);
        }
        double roll = caster.getRandom().nextDouble() * totalWeight;
        for (Holder.Reference<Effect> holder : all) {
            roll -= weightFor(holder);
            if (roll <= 0.0) {
                return holder;
            }
        }
        return all.get(all.size() - 1); // fallback for floating-point drift
    }

    private static double weightFor(Holder.Reference<Effect> holder) {
        int cost = Math.max(1, effectCost(holder));
        return 1.0 / Math.pow(cost, REDSTONE_RANDOM_LOW_BIAS);
    }

    private static void explodeTable(ServerLevel level, BlockPos pos, ServerPlayer caster) {
        caster.displayClientMessage(Component.literal("The table couldn't take it..."), true);
        level.explode(caster, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
        level.removeBlock(pos, false);
    }

    private static void mirrorBackfire(ServerPlayer caster, int durationTicks) {
        List<Holder.Reference<Effect>> curses = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().category() == EffectCategory.CURSE)
                .toList();
        if (curses.isEmpty()) {
            return;
        }
        Holder.Reference<Effect> curse = curses.get(caster.getRandom().nextInt(curses.size()));
        EffectManager.apply(caster, curse, durationTicks, null);
        caster.displayClientMessage(Component.literal("The ritual backfires — right onto you."), true);
    }

    private static void triggerRandomNeutral(ServerLevel level, ServerPlayer caster, int durationTicks) {
        List<Holder.Reference<BewitchmentEvent>> neutrals = WitchModRegistries.EVENT_REGISTRY.holders()
                // Mirror is the dedicated backfire branch above, not a regular neutral outcome here.
                .filter(holder -> holder.value().category() == EventCategory.NEUTRAL && !holder.key().location().getPath().equals("mirror"))
                .toList();
        if (neutrals.isEmpty()) {
            return;
        }
        Holder.Reference<BewitchmentEvent> neutral = neutrals.get(caster.getRandom().nextInt(neutrals.size()));
        neutral.value().start(level, caster, durationTicks);
        DiscoveryManager.markEventDiscovered(caster, neutral.key().location());
        caster.displayClientMessage(Component.literal("Nothing quite... landed. Something else happened instead."), true);
    }
}
