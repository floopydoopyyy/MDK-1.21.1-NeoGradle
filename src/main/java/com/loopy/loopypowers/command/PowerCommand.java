package com.loopy.loopypowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.loopy.loopypowers.ui.CooldownUI;
import com.loopy.loopypowers.manager.AbilityTypes;
import com.loopy.loopypowers.manager.PlayerDataStore;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;
import java.util.function.Supplier;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = "loopypowers")
public class PowerCommand {

    // Arguments
    private static final String ARG_POWER     = "power";
    private static final String ARG_TARGETS   = "targets";
    private static final String ARG_HELP_TYPE = "type";
    private static final String ARG_LEVEL     = "level";

    // ---------- Power registry ----------
    private static final Map<String, Supplier<? extends PowerInterface>> POWERS = new LinkedHashMap<>();
    static {
        POWERS.put("speed",         SpeedPower::new);
        POWERS.put("fire",          FirePower::new);
        POWERS.put("teleportation", TeleportPower::new);
        POWERS.put("lightning",     LightningPower::new);
        POWERS.put("flight",        FlightPower::new);
        POWERS.put("blood",         BloodPower::new);
        POWERS.put("sound",         SoundPower::new);
        POWERS.put("strength",      StrengthPower::new);
        POWERS.put("explosion",     ExplosionPower::new);
        POWERS.put("nature",        NaturePower::new);
        POWERS.put("ice",           IcePower::new);
        POWERS.put("fortune",       FortunePower::new);
        POWERS.put("darkness",      DarknessPower::new);
        POWERS.put("healing",       HealingPower::new);
        POWERS.put("psychic",       PsychicPower::new);
        POWERS.put("cosmic",        CosmicPower::new);
        POWERS.put("telekinesis",   TelekinesisPower::new);
        POWERS.put("dimensional",   DimensionalPower::new);
        // "random" is handled specially in set()
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_POWERS =
            (ctx, builder) -> {
                for (String k : POWERS.keySet()) builder.suggest(k);
                builder.suggest("random");
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_HELP_TYPES =
            (ctx, builder) -> {
                builder.suggest("overview");
                builder.suggest("passive");
                builder.suggest("primary");
                builder.suggest("secondary");
                builder.suggest("ultimate");
                return builder.buildFuture();
            };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("power")

                        // LIST - op
                        // lists all online players, their power, and level
                        .then(literal("list")
                                .requires(src -> src.hasPermission(2))
                                .executes(PowerCommand::listPowers)
                        )

                        // SHUFFLE - op
                        // assigns random powers
                        .then(literal("shuffle")
                                .requires(src -> src.hasPermission(2))
                                // /power shuffle (all online)
                                .executes(PowerCommand::shuffleAll)
                                // /power shuffle <selector>
                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                        .executes(PowerCommand::shuffleTargets)
                                )
                        )

                        // SET - op
                        .then(literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(argument(ARG_POWER, word())
                                        .suggests(SUGGEST_POWERS)

                                        // /power set <power>
                                        .executes(PowerCommand::setPowerSelf)

                                        // /power set <power> <selector>
                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(PowerCommand::setPowerTargets)
                                        )
                                )
                        )

                        // QUERY - op
                        .then(literal("query")
                                // /power query
                                .executes(PowerCommand::querySelf)
                                // /power query <selector>
                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                        .executes(PowerCommand::queryTargets)
                                )
                        )

                        // REMOVE - op
                        .then(literal("remove")
                                .requires(src -> src.hasPermission(2))
                                // /power remove
                                .executes(PowerCommand::removeSelf)
                                // /power remove <selector>
                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                        .executes(PowerCommand::removeTargets)
                                )
                        )

                        // CLEAR - op
                        // wipes both in-memory state AND the persistent save file
                        .then(literal("clear")
                                .requires(src -> src.hasPermission(2))
                                // /power clear
                                .executes(PowerCommand::clearSelf)
                                // /power clear <selector>
                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                        .executes(PowerCommand::clearTargets)
                                )
                        )

                        // LEVEL - op
                        // directly sets a player's level (1–3) without changing their power
                        .then(literal("level")
                                .requires(src -> src.hasPermission(2))
                                // /power level <1-3>
                                .then(argument(ARG_LEVEL, integer(1, 3))
                                        .executes(PowerCommand::setLevelSelf)
                                        // /power level <1-3> <selector>
                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(PowerCommand::setLevelTargets)
                                        )
                                )
                        )

                        // LEVELUP - op
                        // bumps a player's level by one step (1→2, 2→3, caps at 3)
                        .then(literal("levelup")
                                .requires(src -> src.hasPermission(2))
                                // /power levelup
                                .executes(PowerCommand::levelUpSelf)
                                // /power levelup <selector>
                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                        .executes(PowerCommand::levelUpTargets)
                                )
                        )

                        // HELP - no op
                        .then(literal("help")
                                .then(argument(ARG_HELP_TYPE, word())
                                        .suggests(SUGGEST_HELP_TYPES)

                                        // /power help <type>    (uses caller's power)
                                        .executes(PowerCommand::helpSelfPower)

                                        // /power help <type> <powername>
                                        .then(argument(ARG_POWER, word())
                                                .suggests(SUGGEST_POWERS)
                                                .executes(PowerCommand::helpNamedPower)
                                        )
                                )
                        )

                        // DEBUG - op
                        .then(literal("debug")
                                .requires(src -> src.hasPermission(2))

                                // /power debug resetstate [selector]
                                // calls onRemove -> onAssign cleanly to reboot state maps without wiping save data
                                .then(literal("resetstate")
                                        .executes(PowerCommand::debugResetStateSelf)
                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(PowerCommand::debugResetStateTargets)
                                        )
                                )

                                // /power debug clearcooldowns [selector]
                                .then(literal("clearcooldowns")
                                        .executes(PowerCommand::debugClearAllCooldownsSelf)
                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(PowerCommand::debugClearAllCooldownsTargets)
                                        )
                                )

                                // /power debug clearcooldown <primary|secondary|ultimate|all> [selector]
                                .then(literal("clearcooldown")
                                        .then(literal("primary")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, AbilityTypes.PRIMARY))
                                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, AbilityTypes.PRIMARY))
                                                )
                                        )
                                        .then(literal("secondary")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, AbilityTypes.SECONDARY))
                                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, AbilityTypes.SECONDARY))
                                                )
                                        )
                                        .then(literal("ultimate")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, AbilityTypes.ULTIMATE))
                                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, AbilityTypes.ULTIMATE))
                                                )
                                        )
                                        .then(literal("all")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, null))
                                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, null))
                                                )
                                        )
                                )

                                // /power debug forcecooldowns <seconds> [selector]
                                .then(literal("forcecooldowns")
                                        .then(argument("seconds", integer(1))
                                                .executes(PowerCommand::debugForceCooldownsSelf)
                                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                                        .executes(PowerCommand::debugForceCooldownsTargets)
                                                )
                                        )
                                )

                                // /power debug forcecooldown <primary|secondary|ultimate> <seconds> [selector]
                                .then(literal("forcecooldown")
                                        .then(literal("primary")
                                                .then(argument("seconds", integer(1))
                                                        .executes(ctx -> debugForceCooldownSelf(ctx, AbilityTypes.PRIMARY))
                                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                                .executes(ctx -> debugForceCooldownTargets(ctx, AbilityTypes.PRIMARY))
                                                        )
                                                )
                                        )
                                        .then(literal("secondary")
                                                .then(argument("seconds", integer(1))
                                                        .executes(ctx -> debugForceCooldownSelf(ctx, AbilityTypes.SECONDARY))
                                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                                .executes(ctx -> debugForceCooldownTargets(ctx, AbilityTypes.SECONDARY))
                                                        )
                                                )
                                        )
                                        .then(literal("ultimate")
                                                .then(argument("seconds", integer(1))
                                                        .executes(ctx -> debugForceCooldownSelf(ctx, AbilityTypes.ULTIMATE))
                                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                                .executes(ctx -> debugForceCooldownTargets(ctx, AbilityTypes.ULTIMATE))
                                                        )
                                                )
                                        )
                                )

                                // /power debug cooldownmultiplier <multiplier> [selector]
                                .then(literal("cooldownmultiplier")
                                        .then(argument("multiplier", floatArg(0.0f))
                                                .executes(PowerCommand::debugCooldownMultiplierSelf)
                                                .then(argument(ARG_TARGETS, EntityArgument.players())
                                                        .executes(PowerCommand::debugCooldownMultiplierTargets)
                                                )
                                        )
                                )

                                // /power debug save [selector]
                                // force-writes current state to disk without waiting for disconnect
                                .then(literal("save")
                                        .executes(PowerCommand::debugSaveSelf)
                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(PowerCommand::debugSaveTargets)
                                        )
                                )

                                // /power debug reload [selector]
                                // discards in-memory state and reloads from the save file
                                .then(literal("reload")
                                        .executes(PowerCommand::debugReloadSelf)
                                        .then(argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(PowerCommand::debugReloadTargets)
                                        )
                                )

                                // /power debug togglecooldowns
                                // disables or re-enables the cooldown system globally (useful for testing)
                                .then(literal("togglecooldowns")
                                        .executes(PowerCommand::debugToggleCooldowns)
                                )

                                // /power debug onepunch
                                // bypasses rng and armor for the easter egg punch
                                .then(literal("onepunch")
                                        .executes(PowerCommand::debugToggleOnePunch)
                                )
                        )
        );
    }

    // ============================================================
    // LIST
    // ============================================================

    private static int listPowers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<ServerPlayer> players = src.getServer().getPlayerList().getPlayers();

        if (players.isEmpty()) {
            src.sendFailure(Component.literal("No players online."));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("§6--- Online Players ---"), false);
        for (ServerPlayer p : players) {
            PowerInterface power = PowerManager.getPower(p);
            int level = PowerManager.getLevel(p);
            String pName = power != null ? "§a" + power.getName() : "§7NONE";

            src.sendSuccess(() -> Component.literal("§b" + p.getName().getString() + " §f- " + pName + " §8(Lv." + level + ")"), false);
        }

        return 1;
    }

    // ============================================================
    // SHUFFLE
    // ============================================================

    private static int shuffleAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        return applyShuffle(src, src.getServer().getPlayerList().getPlayers());
    }

    private static int shuffleTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return applyShuffle(ctx.getSource(), EntityArgument.getPlayers(ctx, ARG_TARGETS));
    }

    private static int applyShuffle(CommandSourceStack src, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found to shuffle."));
            return 0;
        }

        for (ServerPlayer p : targets) {
            PowerManager.assignRandomPower(p);
            p.sendSystemMessage(Component.literal("§dYour power has been shuffled,"));
        }

        src.sendSuccess(() -> Component.literal("Shuffled powers for " + targets.size() + " player(s)."), true);
        return 1;
    }

    // ============================================================
    // SET
    // ============================================================

    private static int setPowerSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        String powerName = getString(ctx, ARG_POWER);
        applySetPower(ctx.getSource(), List.of(player), powerName);
        return 1;
    }

    private static int setPowerTargets(CommandContext<CommandSourceStack> ctx) {
        String powerName = getString(ctx, ARG_POWER);

        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        applySetPower(ctx.getSource(), targets, powerName);
        return 1;
    }

    private static void applySetPower(CommandSourceStack src, Collection<ServerPlayer> targets, String powerName) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found."));
            return;
        }

        if (powerName.equalsIgnoreCase("random")) {
            for (ServerPlayer p : targets) {
                PowerManager.assignRandomPower(p);
                p.sendSystemMessage(Component.literal("§bPower set to §eRANDOM"));
            }
            src.sendSuccess(() -> Component.literal("Assigned RANDOM power to " + targets.size() + " player(s)."), false);
            return;
        }

        Supplier<? extends PowerInterface> factory = POWERS.get(powerName.toLowerCase(Locale.ROOT));
        if (factory == null) {
            src.sendFailure(Component.literal("Unknown power: " + powerName));
            return;
        }

        for (ServerPlayer p : targets) {
            PowerManager.setPower(p, factory.get());
            p.sendSystemMessage(Component.literal("§bPower set to §a" + powerName.toUpperCase(Locale.ROOT)));
        }

        src.sendSuccess(() -> Component.literal("Set power '" + powerName + "' for " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // QUERY
    // ============================================================

    private static int querySelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        sendPowerQuery(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int queryTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        sendPowerQuery(ctx.getSource(), targets);
        return 1;
    }

    private static void sendPowerQuery(CommandSourceStack src, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found."));
            return;
        }

        for (ServerPlayer p : targets) {
            PowerInterface power = PowerManager.getPower(p);
            String powerName = (power == null) ? "NONE" : power.getName();
            int level = PowerManager.getLevel(p);

            // snapshot of ACTIVE cooldowns
            Map<String, CooldownUI.CooldownInfo> cds = CooldownUI.getCooldownSnapshot(p);

            StringBuilder sb = new StringBuilder();
            sb.append("§b").append(p.getName().getString())
                    .append(" §7-> §fPower: §a").append(powerName)
                    .append(" §7| Level: §b").append(level); // show level alongside power

            // Always show the 3 ability statuses for their CURRENT power (if they have one)
            if (power != null) {
                sb.append("\n§7Cooldowns:");

                for (AbilityTypes type : AbilityTypes.values()) {
                    String key = PowerManager.abilityKey(power, type);
                    CooldownUI.CooldownInfo info = cds.get(key);

                    String abilityName = switch (type) {
                        case PRIMARY   -> power.getPrimaryName();
                        case SECONDARY -> power.getSecondaryName();
                        case ULTIMATE  -> power.getUltimateName();
                    };

                    // show lock state inline with the ability name if the player hasn't unlocked it yet
                    int requiredLevel = switch (type) {
                        case PRIMARY   -> 1;
                        case SECONDARY -> 2;
                        case ULTIMATE  -> 3;
                    };

                    String lockNote = (level < requiredLevel) ? " §c[Lv." + requiredLevel + "]" : "";

                    sb.append("\n  ")
                            .append(type.color).append(abilityName).append(lockNote).append("§7: ");

                    if (info == null) {
                        sb.append("§aREADY");
                    } else {
                        sb.append("§c").append(msToSecondsCeil(info.remainingMs)).append("s");
                        if (info.suffix != null && !info.suffix.isBlank()) {
                            sb.append(" §8").append(info.suffix);
                        }
                    }
                }
            }

            // Also list any EXTRA cooldown keys (not the 3 standard ones), if present
            if (!cds.isEmpty()) {
                Set<String> standardKeys = new HashSet<>();
                if (power != null) {
                    standardKeys.add(PowerManager.abilityKey(power, AbilityTypes.PRIMARY));
                    standardKeys.add(PowerManager.abilityKey(power, AbilityTypes.SECONDARY));
                    standardKeys.add(PowerManager.abilityKey(power, AbilityTypes.ULTIMATE));
                }

                List<String> extras = new ArrayList<>();
                for (var e : cds.entrySet()) {
                    if (standardKeys.contains(e.getKey())) continue;
                    extras.add(e.getKey());
                }

                if (!extras.isEmpty()) {
                    sb.append("\n§7Other cooldowns:");
                    extras.sort(String::compareToIgnoreCase);
                    for (String k : extras) {
                        CooldownUI.CooldownInfo info = cds.get(k);
                        sb.append("\n  §7").append(k).append(": §c")
                                .append(msToSecondsCeil(info.remainingMs)).append("s");
                        if (info.suffix != null && !info.suffix.isBlank()) {
                            sb.append(" §8").append(info.suffix);
                        }
                    }
                }
            }

            src.sendSuccess(() -> Component.literal(sb.toString()), false);
        }
    }

    private static long msToSecondsCeil(long ms) {
        if (ms <= 0) return 0;
        return (ms + 999) / 1000;
    }

    // ============================================================
    // REMOVE
    // ============================================================

    private static int removeSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        removePowerFrom(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int removeTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        removePowerFrom(ctx.getSource(), targets);
        return 1;
    }

    private static void removePowerFrom(CommandSourceStack src, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found."));
            return;
        }

        for (ServerPlayer p : targets) {
            PowerManager.removePower(p); // removes
            p.sendSystemMessage(Component.literal("§cPower removed."));
        }

        src.sendSuccess(() -> Component.literal("Removed power from " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // CLEAR
    // ============================================================

    private static int clearSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        clearPowerData(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int clearTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        clearPowerData(ctx.getSource(), targets);
        return 1;
    }

    /**
     * Wipes both in-memory state and the persistent save file.
     * Literally leaves no trace on disc, so it should be reassigned on next join event
     */
    private static void clearPowerData(CommandSourceStack src, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found."));
            return;
        }

        for (ServerPlayer p : targets) {
            PowerManager.removePower(p);  // clears in-memory power, level, cooldowns
            PlayerDataStore.delete(p);    // wipes the save file entirely
            p.sendSystemMessage(Component.literal("§cAll power data cleared."));
        }

        src.sendSuccess(() -> Component.literal("Cleared all power data for " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // LEVEL
    // ============================================================

    private static int setLevelSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        int level = getInteger(ctx, ARG_LEVEL);
        applySetLevel(ctx.getSource(), List.of(player), level);
        return 1;
    }

    private static int setLevelTargets(CommandContext<CommandSourceStack> ctx) {
        int level = getInteger(ctx, ARG_LEVEL);

        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        applySetLevel(ctx.getSource(), targets, level);
        return 1;
    }

    private static void applySetLevel(CommandSourceStack src, Collection<ServerPlayer> targets, int level) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found."));
            return;
        }

        for (ServerPlayer p : targets) {
            PowerManager.setLevel(p, level);
            PlayerDataStore.save(p); // persist immediately
            p.sendSystemMessage(Component.literal("§bYour power level was set to §e" + level + "§b."));
        }

        src.sendSuccess(() -> Component.literal("Set level " + level + " for " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // LEVELUP
    // ============================================================

    private static int levelUpSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        applyLevelUp(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int levelUpTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        applyLevelUp(ctx.getSource(), targets);
        return 1;
    }

    private static void applyLevelUp(CommandSourceStack src, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No targets found."));
            return;
        }

        int changed = 0;
        for (ServerPlayer p : targets) {
            int before = PowerManager.getLevel(p);
            if (before >= 3) {
                p.sendSystemMessage(Component.literal("§cYour power is already at maximum level."));
                continue;
            }
            PowerManager.levelUp(p);   // sends the evolution message to the player
            PlayerDataStore.save(p);   // persist immediately
            changed++;
        }

        int finalChanged = changed;
        src.sendSuccess(() -> Component.literal("Levelled up " + finalChanged + " player(s)."), false);
    }

    // ============================================================
    // HELP
    // ============================================================

    private static int helpSelfPower(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        String type = getString(ctx, ARG_HELP_TYPE).toLowerCase(Locale.ROOT);

        PowerInterface power = PowerManager.getPower(player);
        if (power == null) {
            ctx.getSource().sendFailure(Component.literal("You have no power. Use /power set <power>."));
            return 0;
        }

        // pass the player's actual level so overview can show lock status
        sendHelp(ctx.getSource(), type, power, PowerManager.getLevel(player));
        return 1;
    }

    private static int helpNamedPower(CommandContext<CommandSourceStack> ctx) {
        String type      = getString(ctx, ARG_HELP_TYPE).toLowerCase(Locale.ROOT);
        String powerName = getString(ctx, ARG_POWER).toLowerCase(Locale.ROOT);

        if (powerName.equals("random")) {
            ctx.getSource().sendFailure(Component.literal("Help is not available for 'random'. Pick a specific power."));
            return 0;
        }

        Supplier<? extends PowerInterface> factory = POWERS.get(powerName);
        if (factory == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown power: " + powerName));
            return 0;
        }

        // no player-level context here — overview will show generic level requirements instead
        sendHelp(ctx.getSource(), type, factory.get(), 0);
        return 1;
    }

    /**
     * Sends a help message for a given power and section type.
     *
     * playerLevel controls lock display in "overview":
     * 0         → no player context; shows generic "Lv.X required" notes
     * 1 / 2 / 3 → player's actual level; shows LOCKED on abilities they can't yet use
     */
    private static void sendHelp(CommandSourceStack src, String type, PowerInterface power, int playerLevel) {
        Component msg;

        switch (type) {
            case "overview" -> {
                // lock suffix shown next to ability names that aren't yet available
                String secLock = getLockSuffix(playerLevel, 2);
                String ultLock = getLockSuffix(playerLevel, 3);

                msg = Component.literal(
                        "§b" + power.getName() + "§f\n" +
                                "§7" + power.getPassiveName() + ": §f" + power.getPassiveDescription() + "\n\n" +
                                "§7" + power.getPrimaryName() + ": §f" + power.getPrimaryDescription() + "\n\n" +
                                "§7" + power.getSecondaryName() + secLock + ": §f" + power.getSecondaryDescription() + "\n\n" +
                                "§7" + power.getUltimateName()  + ultLock + ": §f" + power.getUltimateDescription() + "\n\n" +
                                "§7Overview: §f" + power.getOverviewDescription()
                );
            }

            case "passive" -> msg = Component.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getPassiveName() + ": §f" + power.getPassiveDescription()
            );

            case "primary" -> msg = Component.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getPrimaryName() + " §8(" + cooldownSeconds(power.getPrimaryCooldownMs()) + "s cooldown)§f\n" +
                            "§7" + power.getPrimaryName() + ": §f" + power.getPrimaryDescription()
            );

            case "secondary" -> msg = Component.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getSecondaryName() + " §8(" + cooldownSeconds(power.getSecondaryCooldownMs()) + "s cooldown)§f\n" +
                            "§7" + power.getSecondaryName() + ": §f" + power.getSecondaryDescription()
            );

            case "ultimate" -> msg = Component.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getUltimateName() + " §8(" + cooldownSeconds(power.getUltimateCooldownMs()) + "s cooldown)§f\n" +
                            "§7" + power.getUltimateName() + ": §f" + power.getUltimateDescription()
            );

            default -> {
                src.sendFailure(Component.literal("Unknown help type: " + type));
                return;
            }
        }

        src.sendSuccess(() -> msg, false);
    }

    /**
     * Returns the annotation appended to an ability name in the overview.
     *
     * playerLevel == 0  → generic requirement shown to anyone browsing a named power
     * playerLevel < req → §c[LOCKED] — player can see it but can't use it yet
     * playerLevel >= req → empty string — unlocked, no annotation needed
     */
    private static String getLockSuffix(int playerLevel, int requiredLevel) {
        if (playerLevel == 0)               return " §8(Lv." + requiredLevel + " required)";
        if (playerLevel < requiredLevel)    return " §c[LOCKED]";
        return "";
    }

    private static long cooldownSeconds(long cooldownMs) {
        // converts and rounds up millisecond cooldowns
        if (cooldownMs <= 0) return 0;
        return (cooldownMs + 999) / 1000;
    }

    // ============================================================
    // DEBUG
    // ============================================================

    // ---------- Debug: resetstate ----------

    private static int debugResetStateSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        applyResetState(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugResetStateTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        applyResetState(ctx.getSource(), targets);
        return 1;
    }

    private static void applyResetState(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer p : targets) {
            PowerInterface power = PowerManager.getPower(p);
            if (power != null) {
                // Call onRemove to wipe active state memory
                power.onRemove(p);
                // Immediately call onAssign to rebuild the state
                power.onAssign(p);
                p.sendSystemMessage(Component.literal("§eYour power state has been forcibly reset."));
                count++;
            }
        }
        int finalCount = count;
        src.sendSuccess(() -> Component.literal("Reset power state for " + finalCount + " player(s)."), true);
    }

    // ---------- Debug: forcecooldowns ----------

    private static int debugForceCooldownsSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        int seconds = getInteger(ctx, "seconds");
        debugForceCooldowns(ctx.getSource(), List.of(player), seconds);
        return 1;
    }

    private static int debugForceCooldownsTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        int seconds = getInteger(ctx, "seconds");
        debugForceCooldowns(ctx.getSource(), targets, seconds);
        return 1;
    }

    private static void debugForceCooldowns(CommandSourceStack src, Collection<ServerPlayer> targets, int seconds) {
        long ms = seconds * 1000L;
        for (ServerPlayer p : targets) {
            PowerInterface power = PowerManager.getPower(p);
            if (power != null) {
                PowerManager.startCooldown(p, PowerManager.abilityKey(power, AbilityTypes.PRIMARY), ms);
                PowerManager.startCooldown(p, PowerManager.abilityKey(power, AbilityTypes.SECONDARY), ms);
                PowerManager.startCooldown(p, PowerManager.abilityKey(power, AbilityTypes.ULTIMATE), ms);
                p.sendSystemMessage(Component.literal("§eAll abilities forced on cooldown for " + seconds + "s."));
            }
        }
        src.sendSuccess(() -> Component.literal("Forced all abilities on cooldown for " + targets.size() + " player(s)."), false);
    }

    // ---------- Debug: forcecooldown ----------

    private static int debugForceCooldownSelf(CommandContext<CommandSourceStack> ctx, AbilityTypes type) {
        ServerPlayer player = requirePlayer(ctx);
        int seconds = getInteger(ctx, "seconds");
        debugForceCooldown(ctx.getSource(), List.of(player), type, seconds);
        return 1;
    }

    private static int debugForceCooldownTargets(CommandContext<CommandSourceStack> ctx, AbilityTypes type) throws CommandSyntaxException {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        int seconds = getInteger(ctx, "seconds");
        debugForceCooldown(ctx.getSource(), targets, type, seconds);
        return 1;
    }

    private static void debugForceCooldown(CommandSourceStack src, Collection<ServerPlayer> targets, AbilityTypes type, int seconds) {
        long ms = seconds * 1000L;
        for (ServerPlayer p : targets) {
            PowerInterface power = PowerManager.getPower(p);
            if (power != null) {
                PowerManager.startCooldown(p, PowerManager.abilityKey(power, type), ms);
                p.sendSystemMessage(Component.literal("§e" + type.name() + " forced on cooldown for " + seconds + "s."));
            }
        }
        src.sendSuccess(() -> Component.literal("Forced " + type.name() + " cooldown for " + targets.size() + " player(s)."), false);
    }

    // ---------- Debug: cooldownmultiplier ----------

    private static int debugCooldownMultiplierSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        float mult = getFloat(ctx, "multiplier");
        debugCooldownMultiplier(ctx.getSource(), List.of(player), mult);
        return 1;
    }

    private static int debugCooldownMultiplierTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        float mult = getFloat(ctx, "multiplier");
        debugCooldownMultiplier(ctx.getSource(), targets, mult);
        return 1;
    }

    private static void debugCooldownMultiplier(CommandSourceStack src, Collection<ServerPlayer> targets, float mult) {
        for (ServerPlayer p : targets) {
            PowerManager.setPlayerCooldownMultiplier(p, mult);
            p.sendSystemMessage(Component.literal("§aYour cooldown multiplier was set to " + mult + "x."));
        }
        src.sendSuccess(() -> Component.literal("Set cooldown multiplier to " + mult + "x for " + targets.size() + " player(s)."), false);
    }

    private static int debugClearAllCooldownsSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        debugClearAllCooldowns(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugClearAllCooldownsTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        debugClearAllCooldowns(ctx.getSource(), targets);
        return 1;
    }

    private static void debugClearAllCooldowns(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            PowerManager.clearAllCooldowns(p);
            p.sendSystemMessage(Component.literal("§aAll cooldowns cleared."));
        }
        src.sendSuccess(() -> Component.literal("Cleared ALL cooldowns for " + targets.size() + " player(s)."), false);
    }

    private static int debugClearAbilityCooldownsSelf(CommandContext<CommandSourceStack> ctx, AbilityTypes typeOrNullAll) {
        ServerPlayer player = requirePlayer(ctx);
        debugClearAbilityCooldowns(ctx.getSource(), List.of(player), typeOrNullAll);
        return 1;
    }

    private static int debugClearAbilityCooldownsTargets(CommandContext<CommandSourceStack> ctx, AbilityTypes typeOrNullAll) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        debugClearAbilityCooldowns(ctx.getSource(), targets, typeOrNullAll);
        return 1;
    }

    /**
     * Clears cooldowns ONLY for the player's CURRENT power ability keys.
     * typeOrNullAll == null => clears primary+secondary+ultimate.
     */
    private static void debugClearAbilityCooldowns(CommandSourceStack src, Collection<ServerPlayer> targets, AbilityTypes typeOrNullAll) {
        int changed = 0;

        for (ServerPlayer p : targets) {
            PowerInterface power = PowerManager.getPower(p);
            if (power == null) continue;

            if (typeOrNullAll == null) {
                // clears all three ability cooldowns
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, AbilityTypes.PRIMARY));
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, AbilityTypes.SECONDARY));
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, AbilityTypes.ULTIMATE));
                p.sendSystemMessage(Component.literal("§aPrimary/Secondary/Ultimate cooldowns cleared."));
            } else {
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, typeOrNullAll));
                p.sendSystemMessage(Component.literal("§a" + typeOrNullAll.name() + " cooldown cleared."));
            }

            changed++;
        }

        int finalChanged = changed;
        src.sendSuccess(() -> Component.literal("Cleared ability cooldowns for " + finalChanged + " player(s)."), false);
    }

    // ---------- Debug: save / reload ----------

    private static int debugSaveSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        debugSave(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugSaveTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        debugSave(ctx.getSource(), targets);
        return 1;
    }

    // force writes current in-memory state to disc immediately
    private static void debugSave(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            PlayerDataStore.save(p);
            p.sendSystemMessage(Component.literal("§aPower data saved to disc."));
        }
        src.sendSuccess(() -> Component.literal("Saved data for " + targets.size() + " player(s)."), false);
    }

    private static int debugReloadSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        debugReload(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugReloadTargets(CommandContext<CommandSourceStack> ctx) {
        final Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid selector."));
            return 0;
        }

        debugReload(ctx.getSource(), targets);
        return 1;
    }

    // discards current in-memory state and re-applies from the save file
    private static void debugReload(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            PowerManager.removePower(p);  // clear current state first so onAssign does the thing cleanly
            PlayerDataStore.load(p);
            p.sendSystemMessage(Component.literal("§aPower data reloaded from disk."));
        }
        src.sendSuccess(() -> Component.literal("Reloaded data for " + targets.size() + " player(s)."), false);
    }

    // ---------- Debug: toggle cooldowns ----------

    // tracks the current disabled state so toggle knows which way to flip
    private static boolean cooldownsCurrentlyDisabled = false;

    private static int debugToggleCooldowns(CommandContext<CommandSourceStack> ctx) {
        cooldownsCurrentlyDisabled = !cooldownsCurrentlyDisabled;
        PowerManager.setCooldownsDisabled(cooldownsCurrentlyDisabled);

        String state = cooldownsCurrentlyDisabled ? "§cDISABLED" : "§aENABLED";
        ctx.getSource().sendSuccess(() -> Component.literal("Cooldown system is now " + state + "§f."), true);
        return 1;
    }

    // ---------- Debug: toggle onepunch ----------

    private static int debugToggleOnePunch(CommandContext<CommandSourceStack> ctx) {
        StrengthPower.onePunchDebugEnabled = !StrengthPower.onePunchDebugEnabled;
        String state = StrengthPower.onePunchDebugEnabled ? "§aENABLED" : "§cDISABLED";
        ctx.getSource().sendSuccess(() -> Component.literal("One Punch mode is now " + state + "§f."), true);
        return 1;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            throw new RuntimeException("This command must be run by a player (or provide a selector).");
        }
    }
}