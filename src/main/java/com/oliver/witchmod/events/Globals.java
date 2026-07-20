package com.oliver.witchmod.events;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.events.globals.*;

/**
 * Registers all 15 globals (master-spec Section 8). Implemented as immediate, server-wide triggers rather
 * than persistently-tracked events; see individual class comments (e.g. {@link GlobalSilence}) for the
 * specific simplifications made. ("Who?" was cut from the roster in Phase C per Section 8.)
 */
public final class Globals {
    public static final DeferredHolder<BewitchmentEvent, GlobalInventoryShuffle> INVENTORY_SHUFFLE =
            register("inventory_shuffle", GlobalInventoryShuffle::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalRussianRoulette> RUSSIAN_ROULETTE =
            register("russian_roulette", GlobalRussianRoulette::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalPlayerShuffle> PLAYER_SHUFFLE = register("player_shuffle", GlobalPlayerShuffle::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalHotPotato> HOT_POTATO = register("hot_potato", GlobalHotPotato::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalSpotShuffle> SPOT_SHUFFLE = register("spot_shuffle", GlobalSpotShuffle::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalGravityFlip> GRAVITY_FLIP = register("gravity_flip", GlobalGravityFlip::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalPartyTime> PARTY_TIME = register("party_time", GlobalPartyTime::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalAuction> AUCTION = register("auction", GlobalAuction::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalApocalypse> APOCALYPSE = register("apocalypse", GlobalApocalypse::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalAporkalypse> APORKALYPSE = register("aporkalypse", GlobalAporkalypse::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalTntRain> TNT_RAIN = register("tnt_rain", GlobalTntRain::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalSilence> SILENCE = register("silence", GlobalSilence::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalFireworkShow> FIREWORK_SHOW = register("firework_show", GlobalFireworkShow::new);
    public static final DeferredHolder<BewitchmentEvent, GlobalFloorIsLava> FLOOR_IS_LAVA = register("floor_is_lava", GlobalFloorIsLava::new);
    // "Who?" was cut from the roster (master-spec Section 8) — its class was deleted in Phase C.
    public static final DeferredHolder<BewitchmentEvent, GlobalGamble> GAMBLE = register("gamble", GlobalGamble::new);

    private Globals() {}

    private static <T extends BewitchmentEvent> DeferredHolder<BewitchmentEvent, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EVENTS.register(name, factory);
    }

    /** Forces this class to load (and thus register its globals) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
