package com.loopy.loopypowers.effect;

import com.loopy.loopypowers.Loopypowers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Loopypowers.MOD_ID);

    // Register each effect
    // Replace the placeholders (e.g., FateEffect) with your actual ported classes
    public static final DeferredHolder<MobEffect, MobEffect> FATE = MOB_EFFECTS.register("fate", FateEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> BLOODBOUND = MOB_EFFECTS.register("bloodbound", BloodBoundEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> BLEED = MOB_EFFECTS.register("bleed", BleedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> EXPOSED = MOB_EFFECTS.register("exposed", ExposedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> TETHERED = MOB_EFFECTS.register("tethered", TetheredEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> STUN = MOB_EFFECTS.register("stunned", StunEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> DEEPFREEZE = MOB_EFFECTS.register("deepfreeze", DeepFreezeEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> FRACTURED = MOB_EFFECTS.register("fractured", FracturedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> COMPELLED = MOB_EFFECTS.register("compelled", CompelledEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> POSSESSED = MOB_EFFECTS.register("possessed", PossessedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> GROUNDED = MOB_EFFECTS.register("grounded", GroundedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> BRACED = MOB_EFFECTS.register("braced", BracedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> DISPLACED = MOB_EFFECTS.register("displaced", DisplacedEffect::new);

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}