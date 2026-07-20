package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.oliver.witchmod.WitchMod;

public final class WitchModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WitchMod.MODID);

    /**
     * Persists across logout/relog and (per CLAUDE.md section 2.7: curses/blessings do NOT expire on
     * death by default) across death too.
     */
    public static final Supplier<AttachmentType<ActiveEffects>> ACTIVE_EFFECTS = ATTACHMENT_TYPES.register("active_effects",
            () -> AttachmentType.builder(ActiveEffects::empty)
                    .serialize(ActiveEffects.CODEC)
                    .copyOnDeath()
                    .build());

    /** Tracks which Global events are currently afflicting this player — see {@link ActiveAfflictions}. */
    public static final Supplier<AttachmentType<ActiveAfflictions>> ACTIVE_AFFLICTIONS = ATTACHMENT_TYPES.register("active_afflictions",
            () -> AttachmentType.builder(ActiveAfflictions::empty)
                    .serialize(ActiveAfflictions.CODEC)
                    .copyOnDeath()
                    .build());

    private static final Codec<Set<ResourceLocation>> RESOURCE_LOCATION_SET_CODEC =
            ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    /** Which curses/blessings this player has discovered (CLAUDE.md section 2.7) — see {@link DiscoveryManager}. */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED_EFFECTS = ATTACHMENT_TYPES.register("discovered_effects",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    .copyOnDeath()
                    .build());

    /** Which neutrals/globals this player has discovered — see {@link DiscoveryManager}. */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED_EVENTS = ATTACHMENT_TYPES.register("discovered_events",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    .copyOnDeath()
                    .build());

    /** Game time of this player's first-ever join, -1 if not yet recorded — see {@link GracePeriod}. */
    public static final Supplier<AttachmentType<Long>> FIRST_SEEN_TICK = ATTACHMENT_TYPES.register("first_seen_tick",
            () -> AttachmentType.builder(() -> -1L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    /**
     * Thirst points for the Thirst Meter curse (Phase D HUD): {@code -1} = curse not active (bar hidden),
     * {@code 0..THIRST_MAX} = active. Auto-synced to the client so the HUD overlay can read it directly —
     * NeoForge 21.1 attachment sync, no hand-rolled payload needed. See {@code CurseThirstMeter} +
     * {@code client/ThirstHudLayer}.
     */
    public static final Supplier<AttachmentType<Integer>> THIRST = ATTACHMENT_TYPES.register("thirst",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Extra "second stomach" hunger for the Gluttony curse (Phase D HUD): {@code -1} = curse not active
     * (extra row hidden), {@code 0..GLUTTONY_EXTRA_MAX} = active. Auto-synced like {@link #THIRST}; rendered
     * by {@code client/GluttonyHudLayer} as a second hunger row.
     */
    public static final Supplier<AttachmentType<Integer>> GLUTTONY_HUNGER = ATTACHMENT_TYPES.register("gluttony_hunger",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * The game-time tick at which the Loading Screen curse's fake fullscreen overlay should stop (Phase D).
     * Auto-synced: the server sets it to {@code now + duration} when a door/trapdoor/fence-gate use fires
     * the prank, and {@code client/LoadingScreenOverlay} renders while the (synced) world game time is below
     * it. One-shot trigger sync with no hand-rolled payload. {@code 0} = nothing to show.
     */
    public static final Supplier<AttachmentType<Long>> LOADING_SCREEN_END_TICK = ATTACHMENT_TYPES.register("loading_screen_end_tick",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    /** Whether the Chat (Twitch overlay) blessing is active (Phase D): {@code -1} inactive, {@code 1} active. Auto-synced so the client overlay knows to render. */
    public static final Supplier<AttachmentType<Integer>> CHAT_OVERLAY = ATTACHMENT_TYPES.register("chat_overlay",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * The Organised blessing's 9 extra inventory slots (Phase D). Serialized (and copied on death, since
     * curses/blessings persist through death); NOT synced — the vanilla chest menu it's opened through
     * handles slot sync while open. Dropped when the blessing expires (see {@code BlessingOrganised}).
     */
    public static final Supplier<AttachmentType<List<ItemStack>>> ORGANISED_ITEMS = ATTACHMENT_TYPES.register("organised_items",
            () -> AttachmentType.<List<ItemStack>>builder(() -> List.of())
                    .serialize(ItemStack.OPTIONAL_CODEC.listOf())
                    .copyOnDeath()
                    .build());

    // --- Client-side curse flags (Phase D). Each is an auto-synced int: -1 inactive, 1 active. The actual
    // effect (reversed input / window bounce / window rename / camera hijack) runs client-side off the flag.
    public static final Supplier<AttachmentType<Integer>> MOONWALKER_ACTIVE = ATTACHMENT_TYPES.register("moonwalker_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<Integer>> SCREENSAVER_ACTIVE = ATTACHMENT_TYPES.register("screensaver_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<Integer>> MINOR_INCONVENIENCE_ACTIVE = ATTACHMENT_TYPES.register("minor_inconvenience_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** Pacing's dramatic-freeze window end tick (like {@link #LOADING_SCREEN_END_TICK}) — set on an "epic action". */
    public static final Supplier<AttachmentType<Long>> PACING_END_TICK = ATTACHMENT_TYPES.register("pacing_end_tick",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    private WitchModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
