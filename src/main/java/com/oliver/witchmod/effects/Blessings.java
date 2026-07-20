package com.oliver.witchmod.effects;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.blessings.*;

/**
 * Registers all 45 blessings (master-spec Section 6). {@code locked_in} is the prototype id for "Hawk
 * Guy" (Target Block); {@code tools_dont_use_durability} is "Workman"; {@code trainer} is "Personal
 * Trainer" — ids kept stable to avoid breaking saved data, display names are a lang concern. The final
 * block is Phase A's 15 newly-built blessings.
 */
public final class Blessings {
    public static final DeferredHolder<Effect, BlessingFortune> FORTUNE = register("fortune", BlessingFortune::new);
    public static final DeferredHolder<Effect, BlessingPeace> PEACE = register("peace", BlessingPeace::new);
    public static final DeferredHolder<Effect, BlessingLuck> LUCK = register("luck", BlessingLuck::new);
    public static final DeferredHolder<Effect, BlessingFull> FULL = register("full", BlessingFull::new);
    public static final DeferredHolder<Effect, BlessingArmy> ARMY = register("army", BlessingArmy::new);
    public static final DeferredHolder<Effect, BlessingReflect> REFLECT = register("reflect", BlessingReflect::new);
    public static final DeferredHolder<Effect, BlessingSoulBond> SOUL_BOND = register("soul_bond", BlessingSoulBond::new);
    public static final DeferredHolder<Effect, BlessingBodyguard> BODYGUARD = register("bodyguard", BlessingBodyguard::new);
    public static final DeferredHolder<Effect, BlessingTaxMan> TAX_MAN = register("tax_man", BlessingTaxMan::new);
    public static final DeferredHolder<Effect, BlessingHypeMan> HYPE_MAN = register("hype_man", BlessingHypeMan::new);
    public static final DeferredHolder<Effect, BlessingToolsDontUseDurability> TOOLS_DONT_USE_DURABILITY =
            register("tools_dont_use_durability", BlessingToolsDontUseDurability::new);
    public static final DeferredHolder<Effect, BlessingPickpocket> PICKPOCKET = register("pickpocket", BlessingPickpocket::new);
    public static final DeferredHolder<Effect, BlessingWindfall> WINDFALL = register("windfall", BlessingWindfall::new);
    public static final DeferredHolder<Effect, BlessingImmortality> IMMORTALITY = register("immortality", BlessingImmortality::new);
    public static final DeferredHolder<Effect, BlessingSixthSense> SIXTH_SENSE = register("sixth_sense", BlessingSixthSense::new);
    public static final DeferredHolder<Effect, BlessingIronStomach> IRON_STOMACH = register("iron_stomach", BlessingIronStomach::new);
    public static final DeferredHolder<Effect, BlessingIronLung> IRON_LUNG = register("iron_lung", BlessingIronLung::new);
    public static final DeferredHolder<Effect, BlessingAnchor> ANCHOR = register("anchor", BlessingAnchor::new);
    public static final DeferredHolder<Effect, BlessingTwinkletoes> TWINKLETOES = register("twinkletoes", BlessingTwinkletoes::new);
    public static final DeferredHolder<Effect, BlessingTrainer> TRAINER = register("trainer", BlessingTrainer::new);
    public static final DeferredHolder<Effect, BlessingStudious> STUDIOUS = register("studious", BlessingStudious::new);
    public static final DeferredHolder<Effect, BlessingTwistOfFate> TWIST_OF_FATE = register("twist_of_fate", BlessingTwistOfFate::new);
    public static final DeferredHolder<Effect, BlessingCompany> COMPANY = register("company", BlessingCompany::new);
    public static final DeferredHolder<Effect, BlessingOrganised> ORGANISED = register("organised", BlessingOrganised::new);
    public static final DeferredHolder<Effect, BlessingNightowl> NIGHTOWL = register("nightowl", BlessingNightowl::new);
    public static final DeferredHolder<Effect, BlessingSteadyHands> STEADY_HANDS = register("steady_hands", BlessingSteadyHands::new);
    public static final DeferredHolder<Effect, BlessingLockedIn> LOCKED_IN = register("locked_in", BlessingLockedIn::new);
    public static final DeferredHolder<Effect, BlessingMainCharacter> MAIN_CHARACTER = register("main_character", BlessingMainCharacter::new);
    public static final DeferredHolder<Effect, BlessingJesus> JESUS = register("jesus", BlessingJesus::new);

    // Phase A (master-spec Section 16): the 15 previously NOT-PROTOTYPED blessings, now built to the same
    // loosely-functional/command-startable bar as the rest.
    public static final DeferredHolder<Effect, BlessingThickSkinned> THICK_SKINNED = register("thick_skinned", BlessingThickSkinned::new);
    public static final DeferredHolder<Effect, BlessingFarmersSpirit> FARMERS_SPIRIT = register("farmers_spirit", BlessingFarmersSpirit::new);
    public static final DeferredHolder<Effect, BlessingBrute> BRUTE = register("brute", BlessingBrute::new);
    public static final DeferredHolder<Effect, BlessingBlacksmith> BLACKSMITH = register("blacksmith", BlessingBlacksmith::new);
    public static final DeferredHolder<Effect, BlessingUnseen> UNSEEN = register("unseen", BlessingUnseen::new);
    public static final DeferredHolder<Effect, BlessingSilverTongue> SILVER_TONGUE = register("silver_tongue", BlessingSilverTongue::new);
    public static final DeferredHolder<Effect, BlessingHotStuff> HOT_STUFF = register("hot_stuff", BlessingHotStuff::new);
    public static final DeferredHolder<Effect, BlessingBouncy> BOUNCY = register("bouncy", BlessingBouncy::new);
    public static final DeferredHolder<Effect, BlessingExcavation> EXCAVATION = register("excavation", BlessingExcavation::new);
    public static final DeferredHolder<Effect, BlessingAngler> ANGLER = register("angler", BlessingAngler::new);
    public static final DeferredHolder<Effect, BlessingLaughTrack> LAUGH_TRACK = register("laugh_track", BlessingLaughTrack::new);
    public static final DeferredHolder<Effect, BlessingChat> CHAT = register("chat", BlessingChat::new);
    public static final DeferredHolder<Effect, BlessingCivilisation> CIVILISATION = register("civilisation", BlessingCivilisation::new);
    public static final DeferredHolder<Effect, BlessingLowGravity> LOW_GRAVITY = register("low_gravity", BlessingLowGravity::new);
    public static final DeferredHolder<Effect, BlessingLastStand> LAST_STAND = register("last_stand", BlessingLastStand::new);

    private Blessings() {}

    private static <T extends Effect> DeferredHolder<Effect, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EFFECTS.register(name, factory);
    }

    /** Forces this class to load (and thus register its blessings) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
