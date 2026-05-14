package com.loopy.loopypowers.damage;

import com.loopy.loopypowers.Loopypowers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class ModDamageTypes {
    private ModDamageTypes() {}

    // ============================================================
    // REGISTRY KEYS (1.21.1 - ResourceLocation)
    // ============================================================

    public static final ResourceKey<DamageType> BLEED = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "bleed"));
    public static final ResourceKey<DamageType> BIND = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "bind"));
    public static final ResourceKey<DamageType> OVERDRIVE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "overdrive"));
    public static final ResourceKey<DamageType> RUSH = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "rush"));
    public static final ResourceKey<DamageType> FRENZY = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "frenzy"));
    public static final ResourceKey<DamageType> THUNDERCLAP = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "thunderclap"));
    public static final ResourceKey<DamageType> SMITE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "smite"));
    public static final ResourceKey<DamageType> SONIC = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "sonic"));
    public static final ResourceKey<DamageType> SOUND = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "sound"));
    public static final ResourceKey<DamageType> SUPER_EXPLOSION = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "super_explosion"));
    public static final ResourceKey<DamageType> EXPLOSION_NORMAL = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "explosion_normal"));
    public static final ResourceKey<DamageType> ICE_SPIKE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "ice_spike"));
    public static final ResourceKey<DamageType> ICE_SHATTER = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "ice_shatter"));
    public static final ResourceKey<DamageType> ICE_SHOCKWAVE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "ice_shockwave"));
    public static final ResourceKey<DamageType> ICE_BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "ice_beam"));
    public static final ResourceKey<DamageType> THORN = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "thorn"));
    public static final ResourceKey<DamageType> VINE_BIND = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "vine_bind"));
    public static final ResourceKey<DamageType> SLAM = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "slam"));
    public static final ResourceKey<DamageType> RUSH_COLLISION = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "rush_collision"));
    public static final ResourceKey<DamageType> ABSORB = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "absorb"));
    public static final ResourceKey<DamageType> SMOOTHING = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "smoothing"));
    public static final ResourceKey<DamageType> FATE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "fate"));
    public static final ResourceKey<DamageType> COSMIC_RAY = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "cosmic_ray"));
    public static final ResourceKey<DamageType> SHOOTING_STAR = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "shooting_star"));
    public static final ResourceKey<DamageType> BLACK_HOLE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "black_hole"));
    public static final ResourceKey<DamageType> BACKSTAB = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "backstab"));
    public static final ResourceKey<DamageType> ULTIMATE_STAB = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "darkult"));
    public static final ResourceKey<DamageType> PHASE_BURST = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "phase_burst"));
    public static final ResourceKey<DamageType> FRACTURE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "fracture"));
    public static final ResourceKey<DamageType> BET = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "bet"));
    public static final ResourceKey<DamageType> DUEL = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "duel"));
    public static final ResourceKey<DamageType> HOUSE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "house"));
    public static final ResourceKey<DamageType> WALL_COLLISION = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "wall_collision"));
    public static final ResourceKey<DamageType> STRANGLE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "strangle"));
    public static final ResourceKey<DamageType> BLOCK_THROW = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "block_throw"));
    public static final ResourceKey<DamageType> DEBRIS_ORBIT = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "debris_orbit"));
    public static final ResourceKey<DamageType> FIRE_POWER = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "fire_power"));
    public static final ResourceKey<DamageType> FIRE_EXPLOSION = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "fire_explosion"));
    public static final ResourceKey<DamageType> RITUAL = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "ritual"));
    public static final ResourceKey<DamageType> ONEPUNCH = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "onepunch"));
    public static final ResourceKey<DamageType> ABSORBPULSE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "absorbpulse"));
    public static final ResourceKey<DamageType> BLOODSELF = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "bloodself"));

    // ============================================================
    // DAMAGE SOURCE GENERATORS
    // ============================================================

    // HELPER METHOD TO KEEP GENERATORS CLEAN
    private static Holder<DamageType> getHolder(Level level, ResourceKey<DamageType> key) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
    }

    // BLOOD
    public static DamageSource bleed(Level level, Entity attacker) { return new DamageSource(getHolder(level, BLEED), attacker, attacker); }
    public static DamageSource bleed(Level level) { return new DamageSource(getHolder(level, BLEED)); }

    public static DamageSource bind(Level level, Entity attacker) { return new DamageSource(getHolder(level, BIND), attacker, attacker); }
    public static DamageSource bind(Level level) { return new DamageSource(getHolder(level, BIND)); }

    public static DamageSource bloodself(Level level, Entity attacker) { return new DamageSource(getHolder(level, BLOODSELF), attacker, attacker); }
    public static DamageSource bloodself(Level level) { return new DamageSource(getHolder(level, BLOODSELF)); }

    // SPEED / COMBAT
    public static DamageSource overdrive(Level level, Entity attacker) { return new DamageSource(getHolder(level, OVERDRIVE), attacker, attacker); }
    public static DamageSource overdrive(Level level) { return new DamageSource(getHolder(level, OVERDRIVE)); }

    public static DamageSource rush(Level level, Entity attacker) { return new DamageSource(getHolder(level, RUSH), attacker, attacker); }
    public static DamageSource rush(Level level) { return new DamageSource(getHolder(level, RUSH)); }

    public static DamageSource frenzy(Level level, Entity attacker) { return new DamageSource(getHolder(level, FRENZY), attacker, attacker); }
    public static DamageSource frenzy(Level level) { return new DamageSource(getHolder(level, FRENZY)); }

    // LIGHTNING / SOUND
    public static DamageSource thunderclap(Level level, Entity attacker) { return new DamageSource(getHolder(level, THUNDERCLAP), attacker, attacker); }
    public static DamageSource thunderclap(Level level) { return new DamageSource(getHolder(level, THUNDERCLAP)); }

    public static DamageSource smite(Level level, Entity attacker) { return new DamageSource(getHolder(level, SMITE), attacker, attacker); }
    public static DamageSource smite(Level level) { return new DamageSource(getHolder(level, SMITE)); }

    public static DamageSource sonic(Level level, Entity attacker) { return new DamageSource(getHolder(level, SONIC), attacker, attacker); }
    public static DamageSource sonic(Level level) { return new DamageSource(getHolder(level, SONIC)); }

    public static DamageSource sound(Level level, Entity attacker) { return new DamageSource(getHolder(level, SOUND), attacker, attacker); }
    public static DamageSource sound(Level level) { return new DamageSource(getHolder(level, SOUND)); }

    // EXPLOSION
    public static DamageSource superExplosion(Level level, Entity attacker) { return new DamageSource(getHolder(level, SUPER_EXPLOSION), attacker, attacker); }
    public static DamageSource superExplosion(Level level) { return new DamageSource(getHolder(level, SUPER_EXPLOSION)); }

    public static DamageSource explosionNormal(Level level, Entity attacker) { return new DamageSource(getHolder(level, EXPLOSION_NORMAL), attacker, attacker); }
    public static DamageSource explosionNormal(Level level) { return new DamageSource(getHolder(level, EXPLOSION_NORMAL)); }

    // ICE
    public static DamageSource iceSpike(Level level, Entity attacker) { return new DamageSource(getHolder(level, ICE_SPIKE), attacker, attacker); }
    public static DamageSource iceSpike(Level level) { return new DamageSource(getHolder(level, ICE_SPIKE)); }

    public static DamageSource iceShatter(Level level, Entity attacker) { return new DamageSource(getHolder(level, ICE_SHATTER), attacker, attacker); }
    public static DamageSource iceShatter(Level level) { return new DamageSource(getHolder(level, ICE_SHATTER)); }

    public static DamageSource iceShockwave(Level level, Entity attacker) { return new DamageSource(getHolder(level, ICE_SHOCKWAVE), attacker, attacker); }
    public static DamageSource iceShockwave(Level level) { return new DamageSource(getHolder(level, ICE_SHOCKWAVE)); }

    public static DamageSource iceBeam(Level level, Entity attacker) { return new DamageSource(getHolder(level, ICE_BEAM), attacker, attacker); }
    public static DamageSource iceBeam(Level level) { return new DamageSource(getHolder(level, ICE_BEAM)); }

    // NATURE
    public static DamageSource thorn(Level level, Entity attacker) { return new DamageSource(getHolder(level, THORN), attacker, attacker); }
    public static DamageSource thorn(Level level) { return new DamageSource(getHolder(level, THORN)); }

    public static DamageSource vineBind(Level level, Entity attacker) { return new DamageSource(getHolder(level, VINE_BIND), attacker, attacker); }
    public static DamageSource vineBind(Level level) { return new DamageSource(getHolder(level, VINE_BIND)); }

    // STRENGTH
    public static DamageSource slam(Level level, Entity attacker) { return new DamageSource(getHolder(level, SLAM), attacker, attacker); }
    public static DamageSource slam(Level level) { return new DamageSource(getHolder(level, SLAM)); }

    public static DamageSource rushCollision(Level level, Entity attacker) { return new DamageSource(getHolder(level, RUSH_COLLISION), attacker, attacker); }
    public static DamageSource rushCollision(Level level) { return new DamageSource(getHolder(level, RUSH_COLLISION)); }

    public static DamageSource onePunch(Level level, Entity attacker) { return new DamageSource(getHolder(level, ONEPUNCH), attacker, attacker); }
    public static DamageSource onePunch(Level level) { return new DamageSource(getHolder(level, ONEPUNCH)); }

    // HEALING
    public static DamageSource absorb(Level level, Entity attacker) { return new DamageSource(getHolder(level, ABSORB), attacker, attacker); }
    public static DamageSource absorb(Level level) { return new DamageSource(getHolder(level, ABSORB)); }

    public static DamageSource smoothing(Level level, Entity attacker) { return new DamageSource(getHolder(level, SMOOTHING), attacker, attacker); }
    public static DamageSource smoothing(Level level) { return new DamageSource(getHolder(level, SMOOTHING)); }

    public static DamageSource absorbpulse(Level level, Entity attacker) { return new DamageSource(getHolder(level, ABSORBPULSE), attacker, attacker); }
    public static DamageSource absorbpulse(Level level) { return new DamageSource(getHolder(level, ABSORBPULSE)); }

    // COSMIC
    public static DamageSource fate(Level level, Entity attacker) { return new DamageSource(getHolder(level, FATE), attacker, attacker); }
    public static DamageSource fate(Level level) { return new DamageSource(getHolder(level, FATE)); }

    public static DamageSource cosmicRay(Level level, Entity attacker) { return new DamageSource(getHolder(level, COSMIC_RAY), attacker, attacker); }
    public static DamageSource cosmicRay(Level level) { return new DamageSource(getHolder(level, COSMIC_RAY)); }

    public static DamageSource shootingStar(Level level, Entity attacker) { return new DamageSource(getHolder(level, SHOOTING_STAR), attacker, attacker); }
    public static DamageSource shootingStar(Level level) { return new DamageSource(getHolder(level, SHOOTING_STAR)); }

    public static DamageSource blackHole(Level level, Entity attacker) { return new DamageSource(getHolder(level, BLACK_HOLE), attacker, attacker); }
    public static DamageSource blackHole(Level level) { return new DamageSource(getHolder(level, BLACK_HOLE)); }

    // DARKNESS
    public static DamageSource darknessBackstab(Level level, Entity attacker) { return new DamageSource(getHolder(level, BACKSTAB), attacker, attacker); }
    public static DamageSource darknessBackstab(Level level) { return new DamageSource(getHolder(level, BACKSTAB)); }

    public static DamageSource darkUlt(Level level, Entity attacker) { return new DamageSource(getHolder(level, ULTIMATE_STAB), attacker, attacker); }
    public static DamageSource darkUlt(Level level) { return new DamageSource(getHolder(level, ULTIMATE_STAB)); }

    // DIMENSIONAL
    public static DamageSource phaseBurst(Level level, Entity attacker) { return new DamageSource(getHolder(level, PHASE_BURST), attacker, attacker); }
    public static DamageSource phaseBurst(Level level) { return new DamageSource(getHolder(level, PHASE_BURST)); }

    public static DamageSource fracture(Level level, Entity attacker) { return new DamageSource(getHolder(level, FRACTURE), attacker, attacker); }
    public static DamageSource fracture(Level level) { return new DamageSource(getHolder(level, FRACTURE)); }

    // FORTUNE
    public static DamageSource bet(Level level, Entity attacker) { return new DamageSource(getHolder(level, BET), attacker, attacker); }
    public static DamageSource bet(Level level) { return new DamageSource(getHolder(level, BET)); }

    public static DamageSource duel(Level level, Entity attacker) { return new DamageSource(getHolder(level, DUEL), attacker, attacker); }
    public static DamageSource duel(Level level) { return new DamageSource(getHolder(level, DUEL)); }

    public static DamageSource house(Level level, Entity attacker) { return new DamageSource(getHolder(level, HOUSE), attacker, attacker); }
    public static DamageSource house(Level level) { return new DamageSource(getHolder(level, HOUSE)); }

    // TELEKINESIS
    public static DamageSource wallCollision(Level level, Entity attacker) { return new DamageSource(getHolder(level, WALL_COLLISION), attacker, attacker); }
    public static DamageSource wallCollision(Level level) { return new DamageSource(getHolder(level, WALL_COLLISION)); }

    public static DamageSource strangle(Level level, Entity attacker) { return new DamageSource(getHolder(level, STRANGLE), attacker, attacker); }
    public static DamageSource strangle(Level level) { return new DamageSource(getHolder(level, STRANGLE)); }

    public static DamageSource blockThrow(Level level, Entity attacker) { return new DamageSource(getHolder(level, BLOCK_THROW), attacker, attacker); }
    public static DamageSource blockThrow(Level level) { return new DamageSource(getHolder(level, BLOCK_THROW)); }

    public static DamageSource debrisOrbit(Level level, Entity attacker) { return new DamageSource(getHolder(level, DEBRIS_ORBIT), attacker, attacker); }
    public static DamageSource debrisOrbit(Level level) { return new DamageSource(getHolder(level, DEBRIS_ORBIT)); }

    // FIRE
    public static DamageSource firePower(Level level, Entity attacker) { return new DamageSource(getHolder(level, FIRE_POWER), attacker, attacker); }
    public static DamageSource firePower(Level level) { return new DamageSource(getHolder(level, FIRE_POWER)); }

    public static DamageSource fireExplosion(Level level, Entity attacker) { return new DamageSource(getHolder(level, FIRE_EXPLOSION), attacker, attacker); }
    public static DamageSource fireExplosion(Level level) { return new DamageSource(getHolder(level, FIRE_EXPLOSION)); }

    // MISC
    public static DamageSource ritual(Level level, Entity attacker) { return new DamageSource(getHolder(level, RITUAL), attacker, attacker); }
    public static DamageSource ritual(Level level) { return new DamageSource(getHolder(level, RITUAL)); }

    public static void init() {
        // Class load hook
    }
}