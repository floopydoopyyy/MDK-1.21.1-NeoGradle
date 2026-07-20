package com.oliver.witchmod.effects;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.curses.*;

/**
 * Registers all 50 curses (master-spec Section 5). The first block below uses the prototype's original
 * names for several the master spec later renamed (e.g. neutral_mobs_attack_instantly = "Neutral
 * Aggression", loud = "Flat Footed", pidgeon_toed = "Wonky", sick_of_you = "Broken Bonds") — the
 * registry ids are kept stable rather than renamed to avoid breaking saved player data; the display
 * names are a lang-file concern. The final block is Phase A's 7 newly-built curses.
 */
public final class Curses {
    public static final DeferredHolder<Effect, CurseViolence> VIOLENCE = register("violence", CurseViolence::new);
    public static final DeferredHolder<Effect, CurseButterfingers> BUTTERFINGERS = register("butterfingers", CurseButterfingers::new);
    public static final DeferredHolder<Effect, CurseExplosive> EXPLOSIVE = register("explosive", CurseExplosive::new);
    public static final DeferredHolder<Effect, CursePopularity> POPULARITY = register("popularity", CursePopularity::new);
    public static final DeferredHolder<Effect, CurseYap> YAP = register("yap", CurseYap::new);
    public static final DeferredHolder<Effect, CurseGreenAura> GREEN_AURA = register("green_aura", CurseGreenAura::new);
    public static final DeferredHolder<Effect, CurseRepel> REPEL = register("repel", CurseRepel::new);
    public static final DeferredHolder<Effect, CurseEchoes> ECHOES = register("echoes", CurseEchoes::new);
    public static final DeferredHolder<Effect, CurseDelusions> DELUSIONS = register("delusions", CurseDelusions::new);
    public static final DeferredHolder<Effect, CurseGluttony> GLUTTONY = register("gluttony", CurseGluttony::new);
    public static final DeferredHolder<Effect, CurseGassy> GASSY = register("gassy", CurseGassy::new);
    public static final DeferredHolder<Effect, CurseFarmhand> FARMHAND = register("farmhand", CurseFarmhand::new);
    public static final DeferredHolder<Effect, CurseHeavy> HEAVY = register("heavy", CurseHeavy::new);
    public static final DeferredHolder<Effect, CurseSlipperyFeet> SLIPPERY_FEET = register("slippery_feet", CurseSlipperyFeet::new);
    public static final DeferredHolder<Effect, CurseMagnet> MAGNET = register("magnet", CurseMagnet::new);
    public static final DeferredHolder<Effect, CurseNeutralMobsAttackInstantly> NEUTRAL_MOBS_ATTACK_INSTANTLY =
            register("neutral_mobs_attack_instantly", CurseNeutralMobsAttackInstantly::new);
    public static final DeferredHolder<Effect, CurseDwarfism> DWARFISM = register("dwarfism", CurseDwarfism::new);
    public static final DeferredHolder<Effect, CurseScreensaver> SCREENSAVER = register("screensaver", CurseScreensaver::new);
    public static final DeferredHolder<Effect, CurseMinorInconvenience> MINOR_INCONVENIENCE = register("minor_inconvenience", CurseMinorInconvenience::new);
    public static final DeferredHolder<Effect, CurseThirstMeter> THIRST_METER = register("thirst_meter", CurseThirstMeter::new);
    public static final DeferredHolder<Effect, CurseSocialOutcast> SOCIAL_OUTCAST = register("social_outcast", CurseSocialOutcast::new);
    public static final DeferredHolder<Effect, CurseFloorIsLava> FLOOR_IS_LAVA = register("floor_is_lava", CurseFloorIsLava::new);
    public static final DeferredHolder<Effect, CurseHeavyweight> HEAVYWEIGHT = register("heavyweight", CurseHeavyweight::new);
    public static final DeferredHolder<Effect, CurseBadSwimmer> BAD_SWIMMER = register("bad_swimmer", CurseBadSwimmer::new);
    public static final DeferredHolder<Effect, CursePests> PESTS = register("pests", CursePests::new);
    public static final DeferredHolder<Effect, CurseAllergic> ALLERGIC = register("allergic", CurseAllergic::new);
    public static final DeferredHolder<Effect, CurseComicRelief> COMIC_RELIEF = register("comic_relief", CurseComicRelief::new);
    public static final DeferredHolder<Effect, CurseUgly> UGLY = register("ugly", CurseUgly::new);
    public static final DeferredHolder<Effect, CurseTaxes> TAXES = register("taxes", CurseTaxes::new);
    public static final DeferredHolder<Effect, CurseSticky> STICKY = register("sticky", CurseSticky::new);
    public static final DeferredHolder<Effect, CurseBackseatDriver> BACKSEAT_DRIVER = register("backseat_driver", CurseBackseatDriver::new);
    public static final DeferredHolder<Effect, CurseClumsy> CLUMSY = register("clumsy", CurseClumsy::new);
    public static final DeferredHolder<Effect, CurseOversharer> OVERSHARER = register("oversharer", CurseOversharer::new);
    public static final DeferredHolder<Effect, CurseAura> AURA = register("aura", CurseAura::new);
    public static final DeferredHolder<Effect, CurseSickOfYou> SICK_OF_YOU = register("sick_of_you", CurseSickOfYou::new);
    public static final DeferredHolder<Effect, CurseInsomniac> INSOMNIAC = register("insomniac", CurseInsomniac::new);
    public static final DeferredHolder<Effect, CurseLoud> LOUD = register("loud", CurseLoud::new);
    public static final DeferredHolder<Effect, CursePidgeonToed> PIDGEON_TOED = register("pidgeon_toed", CursePidgeonToed::new);
    public static final DeferredHolder<Effect, CurseStickDrift> STICK_DRIFT = register("stick_drift", CurseStickDrift::new);
    public static final DeferredHolder<Effect, CurseBasementDweller> BASEMENT_DWELLER = register("basement_dweller", CurseBasementDweller::new);
    public static final DeferredHolder<Effect, CurseGlassCannon> GLASS_CANNON = register("glass_cannon", CurseGlassCannon::new);
    public static final DeferredHolder<Effect, CurseUncareful> UNCAREFUL = register("uncareful", CurseUncareful::new);
    public static final DeferredHolder<Effect, CurseMansplainer> MANSPLAINER = register("mansplainer", CurseMansplainer::new);

    // Phase A (master-spec Section 16): the 7 previously NOT-PROTOTYPED curses, now built to the same
    // loosely-functional/command-startable bar as the rest. (Uncareful was already present above.)
    public static final DeferredHolder<Effect, CurseSuperExplosive> SUPER_EXPLOSIVE = register("super_explosive", CurseSuperExplosive::new);
    public static final DeferredHolder<Effect, CurseClaustrophobia> CLAUSTROPHOBIA = register("claustrophobia", CurseClaustrophobia::new);
    public static final DeferredHolder<Effect, CurseMoonwalker> MOONWALKER = register("moonwalker", CurseMoonwalker::new);
    public static final DeferredHolder<Effect, CurseSirensCall> SIRENS_CALL = register("sirens_call", CurseSirensCall::new);
    public static final DeferredHolder<Effect, CurseLoadingScreen> LOADING_SCREEN = register("loading_screen", CurseLoadingScreen::new);
    public static final DeferredHolder<Effect, CursePacing> PACING = register("pacing", CursePacing::new);
    public static final DeferredHolder<Effect, CurseTrumpet> TRUMPET = register("trumpet", CurseTrumpet::new);

    private Curses() {}

    private static <T extends Effect> DeferredHolder<Effect, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EFFECTS.register(name, factory);
    }

    /** Forces this class to load (and thus register its curses) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
