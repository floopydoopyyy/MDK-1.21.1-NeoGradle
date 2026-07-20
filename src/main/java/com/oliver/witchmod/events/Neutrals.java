package com.oliver.witchmod.events;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.BewitchmentEvent;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.events.neutrals.*;

/** Registers the 10 neutrals (CLAUDE.md section 4.3/6.3) plus Mirror, the backfire-only special case. */
public final class Neutrals {
    public static final DeferredHolder<BewitchmentEvent, NeutralWooliam> WOOLIAM = register("wooliam", NeutralWooliam::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralDisguise> DISGUISE = register("disguise", NeutralDisguise::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralAnvilAboveHead> ANVIL_ABOVE_HEAD = register("anvil_above_head", NeutralAnvilAboveHead::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralLetter> LETTER = register("letter", NeutralLetter::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralCreeperBehindPlayer> CREEPER_BEHIND_PLAYER =
            register("creeper_behind_player", NeutralCreeperBehindPlayer::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralUselessTrade> USELESS_TRADE = register("useless_trade", NeutralUselessTrade::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralMoovin> MOOVIN = register("moovin", NeutralMoovin::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralMiniFireworkShow> MINI_FIREWORK_SHOW =
            register("mini_firework_show", NeutralMiniFireworkShow::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralMansplaining> MANSPLAINING = register("mansplaining", NeutralMansplaining::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralDamage> DAMAGE = register("damage", NeutralDamage::new);
    public static final DeferredHolder<BewitchmentEvent, NeutralMirror> MIRROR = register("mirror", NeutralMirror::new);

    private Neutrals() {}

    private static <T extends BewitchmentEvent> DeferredHolder<BewitchmentEvent, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EVENTS.register(name, factory);
    }

    /** Forces this class to load (and thus register its neutrals) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
