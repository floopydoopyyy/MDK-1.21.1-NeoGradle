package com.oliver.witchmod.commands;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.AfflictionManager;
import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.GlobalCharge;
import com.oliver.witchmod.data.OrganisedStash;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EventCategory;
import com.oliver.witchmod.data.LedgerLog;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * The {@code /bewitch} command tree (CLAUDE.md section 8). The only entry point into curse/blessing/
 * neutral/global logic until the Bewitching Table (Phase 4) and its UI (Phase 5) exist.
 */
public final class BewitchCommand {
    /** Curses/blessings normally run 30-60 minutes (CLAUDE.md section 2.1); default absent an override. */
    private static final int DEFAULT_EFFECT_DURATION_TICKS = 45 * 60 * 20;
    /** Neutrals/globals are brief flavor events; default absent an override. */
    private static final int DEFAULT_EVENT_DURATION_TICKS = 20 * 20;

    private static final SimpleCommandExceptionType ERROR_WRONG_EVENT_CATEGORY =
            new SimpleCommandExceptionType(Component.translatable("commands.bewitch.event.wrong_category"));

    private BewitchCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("bewitch")
                .requires(source -> source.hasPermission(2))
                .then(applyNode(context))
                .then(removeNode(context))
                .then(clearNode())
                .then(eventNode(context))
                .then(forcestopNode(context))
                .then(Commands.literal("organised").executes(BewitchCommand::openOrganised)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> applyNode(CommandBuildContext context) {
        return Commands.literal("apply")
                .then(Commands.argument("effect", ResourceArgument.resource(context, WitchModRegistries.EFFECT_REGISTRY_KEY))
                        .executes(ctx -> applyEffect(ctx, Collections.singletonList(ctx.getSource().getPlayerOrException()), DEFAULT_EFFECT_DURATION_TICKS))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> applyEffect(ctx, EntityArgument.getPlayers(ctx, "targets"), DEFAULT_EFFECT_DURATION_TICKS))
                                .then(Commands.argument("duration", TimeArgument.time(1))
                                        .executes(ctx -> applyEffect(ctx, EntityArgument.getPlayers(ctx, "targets"), ctx.getArgument("duration", Integer.class))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeNode(CommandBuildContext context) {
        return Commands.literal("remove")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("effect", ResourceArgument.resource(context, WitchModRegistries.EFFECT_REGISTRY_KEY))
                                .executes(BewitchCommand::removeEffect)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearNode() {
        // /bewitch clear [targets] [curses|blessings] — strip all attachments, or just one category.
        return Commands.literal("clear")
                .executes(ctx -> clearEffects(ctx, Collections.singletonList(ctx.getSource().getPlayerOrException()), null))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> clearEffects(ctx, EntityArgument.getPlayers(ctx, "targets"), null))
                        .then(Commands.literal("curses")
                                .executes(ctx -> clearEffects(ctx, EntityArgument.getPlayers(ctx, "targets"), EffectCategory.CURSE)))
                        .then(Commands.literal("blessings")
                                .executes(ctx -> clearEffects(ctx, EntityArgument.getPlayers(ctx, "targets"), EffectCategory.BLESSING))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eventNode(CommandBuildContext context) {
        return Commands.literal("event")
                .then(Commands.literal("neutral")
                        .then(Commands.argument("event", ResourceArgument.resource(context, WitchModRegistries.EVENT_REGISTRY_KEY))
                                .executes(ctx -> startEvent(ctx, EventCategory.NEUTRAL, ctx.getSource().getPlayerOrException(), DEFAULT_EVENT_DURATION_TICKS))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> startEvent(ctx, EventCategory.NEUTRAL, EntityArgument.getPlayer(ctx, "target"), DEFAULT_EVENT_DURATION_TICKS))
                                        .then(Commands.argument("duration", TimeArgument.time(1))
                                                .executes(ctx -> startEvent(ctx, EventCategory.NEUTRAL, EntityArgument.getPlayer(ctx, "target"), ctx.getArgument("duration", Integer.class)))))))
                .then(Commands.literal("global")
                        .then(Commands.argument("event", ResourceArgument.resource(context, WitchModRegistries.EVENT_REGISTRY_KEY))
                                .executes(ctx -> startEvent(ctx, EventCategory.GLOBAL, null, DEFAULT_EVENT_DURATION_TICKS))
                                .then(Commands.argument("initiator", EntityArgument.player())
                                        .executes(ctx -> startEvent(ctx, EventCategory.GLOBAL, EntityArgument.getPlayer(ctx, "initiator"), DEFAULT_EVENT_DURATION_TICKS))
                                        .then(Commands.argument("duration", TimeArgument.time(1))
                                                .executes(ctx -> startEvent(ctx, EventCategory.GLOBAL, EntityArgument.getPlayer(ctx, "initiator"), ctx.getArgument("duration", Integer.class)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> forcestopNode(CommandBuildContext context) {
        return Commands.literal("forcestop")
                .then(Commands.literal("neutral")
                        .then(Commands.argument("event", ResourceArgument.resource(context, WitchModRegistries.EVENT_REGISTRY_KEY))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> stopEvent(ctx, EventCategory.NEUTRAL, EntityArgument.getPlayer(ctx, "target"))))))
                .then(Commands.literal("global")
                        .then(Commands.argument("event", ResourceArgument.resource(context, WitchModRegistries.EVENT_REGISTRY_KEY))
                                .executes(ctx -> stopEvent(ctx, EventCategory.GLOBAL, null))));
    }

    private static int applyEffect(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, int durationTicks) throws CommandSyntaxException {
        Holder.Reference<Effect> effect = ResourceArgument.getResource(ctx, "effect", WitchModRegistries.EFFECT_REGISTRY_KEY);
        ServerPlayer caster = ctx.getSource().getPlayer();
        Optional<String> casterName = Optional.ofNullable(caster).map(p -> p.getName().getString());
        long gameTime = ctx.getSource().getLevel().getGameTime();
        for (ServerPlayer target : targets) {
            EffectManager.apply(target, effect, durationTicks, caster);
            LedgerLog.log(casterName, target.getName().getString(), effect.key().location(), "command", gameTime);
        }
        int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.bewitch.apply.success", effect.key().location().toString(), count), true);
        return count;
    }

    private static int clearEffects(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, @Nullable EffectCategory category) {
        int total = 0;
        for (ServerPlayer target : targets) {
            total += EffectManager.removeAll(target, category);
        }
        int finalTotal = total;
        String what = category == null ? "attachment(s)"
                : (category == EffectCategory.CURSE ? "curse(s)" : "blessing(s)");
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + finalTotal + " " + what), true);
        return total;
    }

    private static int removeEffect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Holder.Reference<Effect> effect = ResourceArgument.getResource(ctx, "effect", WitchModRegistries.EFFECT_REGISTRY_KEY);
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int removed = 0;
        for (ServerPlayer target : targets) {
            if (EffectManager.remove(target, effect)) {
                removed++;
            }
        }
        int finalRemoved = removed;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.bewitch.remove.success", finalRemoved, effect.key().location().toString()), true);
        return removed;
    }

    private static int startEvent(CommandContext<CommandSourceStack> ctx, EventCategory category, @Nullable ServerPlayer initiator, int durationTicks) throws CommandSyntaxException {
        Holder.Reference<BewitchmentEvent> event = ResourceArgument.getResource(ctx, "event", WitchModRegistries.EVENT_REGISTRY_KEY);
        if (event.value().category() != category) {
            throw ERROR_WRONG_EVENT_CATEGORY.create();
        }
        ServerLevel level = ctx.getSource().getLevel();

        if (category == EventCategory.GLOBAL) {
            if (!Config.GLOBALS_ENABLED.get()) {
                ctx.getSource().sendFailure(Component.literal("Globals are disabled on this server."));
                return 0;
            }
            // Globals share ONE server-wide charge (master-spec Section 8). The bare command form
            // (no selector) is a "natural attempt": it rolls the current shared chance and only fires on
            // success, resetting the charge for everyone. Supplying a selector is an operator force-fire
            // (deliberate attribution/testing) that bypasses the roll.
            MinecraftServer server = ctx.getSource().getServer();
            GlobalCharge charge = GlobalCharge.get(server);
            boolean forced = initiator != null;
            if (!forced) {
                float chance = charge.currentSuccessChance(server);
                if (level.getRandom().nextFloat() >= chance) {
                    ctx.getSource().sendFailure(Component.literal(String.format(
                            "The global charge isn't ready — %.0f%% chance right now. It climbs back over a few hours.",
                            chance * 100)));
                    return 0;
                }
            }
            event.value().start(level, initiator, durationTicks);
            // Every online player is Afflicted for its duration and discovers it (they all witnessed it).
            for (ServerPlayer player : level.players()) {
                AfflictionManager.afflict(player, event, durationTicks);
                DiscoveryManager.markEventDiscovered(player, event.key().location());
            }
            charge.reset(server); // a fired global restarts the shared cooldown
        } else {
            event.value().start(level, initiator, durationTicks);
            if (initiator != null) {
                DiscoveryManager.markEventDiscovered(initiator, event.key().location());
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.bewitch.event.success", event.key().location().toString()), true);
        return 1;
    }

    /**
     * Opens the Organised blessing's 9-slot stash as a plain vanilla 1-row chest (Phase D). Command access
     * for now; a survival keybind is the intended follow-up. Anyone may open it — it's their own stash — but
     * the command sits behind the same op gate as the rest of {@code /bewitch}.
     */
    private static int openOrganised(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SimpleContainer container = OrganisedStash.openContainer(player);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, container, 1),
                Component.literal("Organised")));
        return 1;
    }

    private static int stopEvent(CommandContext<CommandSourceStack> ctx, EventCategory category, @Nullable ServerPlayer target) throws CommandSyntaxException {
        Holder.Reference<BewitchmentEvent> event = ResourceArgument.getResource(ctx, "event", WitchModRegistries.EVENT_REGISTRY_KEY);
        if (event.value().category() != category) {
            throw ERROR_WRONG_EVENT_CATEGORY.create();
        }
        ServerLevel level = ctx.getSource().getLevel();
        event.value().stop(level, target);
        if (category == EventCategory.GLOBAL) {
            for (ServerPlayer player : level.players()) {
                AfflictionManager.clear(player, event);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.bewitch.forcestop.success", event.key().location().toString()), true);
        return 1;
    }
}
