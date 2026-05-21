package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.HealingUltPayload;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.loopy.loopypowers.power.BloodPower.ACTIVE_BLEEDS;

public class HealingPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, HealingState> ACTIVE_STATES = new HashMap<>();

    private static class HealingState {
        int passiveDelayTicks = PASSIVE_DELAY;
        int absorbTicks = 0;
        float absorbStored = 0f;

        int ultTicks = 0;
        int ultPhase = -1;
        float lifesteal = 0f;
        float smoothing = 0f;
    }

    private static HealingState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new HealingState());
    }

    // Bio-energy healing particles (Pinkish-Red)
    private static final DustParticleOptions HEAL_DUST = new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.4f), 1.2f);

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("hf_")); // cleanup legacy tags
        ACTIVE_STATES.put(player.getUUID(), new HealingState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("hf_"));
        ACTIVE_STATES.remove(player.getUUID());

        // Strip any buffs/debuffs given by the power
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.GLOWING);
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        HealingState state = getState(player);
        handlePassive(player, state);
        handleAbsorb(player, state);
        handleUltimate(player, state);
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        HealingState state = getState(attacker);
        if (state.lifesteal > 0) {
            attacker.heal(state.lifesteal * 2.0f); // tweak multiplier if needed
        }
    }

    @Override
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        HealingState state = getState(victim);
        state.passiveDelayTicks = PASSIVE_DELAY; // Reset passive regen delay

        // Fast fail for power-specific damage types to prevent infinite recursion
        if (source.is(ModDamageTypes.ABSORB) || source.is(ModDamageTypes.SMOOTHING)) {
            return true;
        }

        // Absorb Shield Logic
        if (state.absorbTicks > 0) {
            float applied = amount * ABSORB_DAMAGE_TAKEN_MULT;
            float absorbed = amount * (1.0f - ABSORB_DAMAGE_TAKEN_MULT);

            state.absorbStored += absorbed;

            float capped = Math.min(state.absorbStored, BURST_MAX_SCALING);
            float intensity = capped / BURST_MAX_SCALING; // 0.0 to 1.0

            victim.hurt(ModDamageTypes.absorb(victim.level()), applied);

            float pitch = 0.8f + (intensity * 1.2f);
            victim.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, pitch);

            int fxCount = 5 + (int)(intensity * 15);
            victim.serverLevel().sendParticles(
                    ParticleTypes.TOTEM_OF_UNDYING,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    fxCount,
                    0.2 + intensity * 0.2, 0.3 + intensity * 0.2, 0.2 + intensity * 0.2,
                    0.01 + intensity * 0.05
            );

            return false; // Cancel original damage
        }

        // Ultimate Smoothing Logic
        if (state.smoothing > 0f) {
            float reduced = amount * (1.0f - state.smoothing);
            victim.hurt(ModDamageTypes.smoothing(victim.level()), reduced);
            return false; // Cancel original damage
        }

        return true;
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final int PASSIVE_DELAY = 100; // delay before healing
    private static final float HEALING = 0.25f; // amount healed per tick

    private void handlePassive(ServerPlayer player, HealingState state) {
        if (!PassiveManager.isEnabled(player)) return;

        if (state.passiveDelayTicks > 0) {
            state.passiveDelayTicks--;
        }

        if (state.passiveDelayTicks <= 0) {
            float maxHeal = player.getMaxHealth();

            // Only emit particles if health is actually going up
            if (player.getHealth() < maxHeal) {
                player.serverLevel().sendParticles(
                        HEAL_DUST,
                        player.getX(),
                        player.getY() + player.getBbHeight() * 0.5,
                        player.getZ(),
                        2, 0.3, 0.5, 0.3, 0.01
                );
            }

            // small continuous heal
            player.heal(HEALING);
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        int removed = 0; // how many debuffs purged

        // power-based effects
        if (BloodPower.cleanseBleed(player)) removed++;
        if (FortunePower.cleanseDuel(player)) removed++;
        if (CosmicPower.cleanseFate(player)) removed++;
        if (IcePower.cleanseFreeze(player)) removed++;
        if (SoundPower.cleanseResonance(player)) removed++;
        if (PsychicPower.cleansePsychic(player)) removed++;
        if (TelekinesisPower.cleanseTelekinesis(player)) removed++;
        if (NaturePower.cleanseVines(player)) removed++;

        // vanilla effects
        for (Holder<MobEffect> effectType : new java.util.ArrayList<>(player.getActiveEffectsMap().keySet())) {
            if (effectType.value().getCategory() == MobEffectCategory.BENEFICIAL) continue;
            player.removeEffect(effectType);
            removed++;
        }

        if (player.isOnFire()) {
            player.clearFire();
            removed++;
        }

        float healAmount = 4.0f + (removed * 2.0f);
        if (healAmount > 10) {
            healAmount = 10;
        }
        player.heal(healAmount);

        w.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
        w.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 3, 0.7, 0.9, 0.7, 0.1);
        w.sendParticles(HEAL_DUST, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 30, 0.6, 0.8, 0.6, 0.05);

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8f, 0.6f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, player.getSoundSource(), 0.6f, 1.2f);

        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10, 0, true, false, true));
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final int ABSORB_DURATION = 40;

    // Damage
    private static final float BURST_BASE_DAMAGE = 9.5f;
    private static final float BURST_DAMAGE_PER_STORED = 4.6f;
    private static final float BURST_MAX_SCALING = 50.0f; // cap

    private static final float ABSORB_DAMAGE_TAKEN_MULT = 0.30f; // player takes this percentage of damage

    // Expelled debuff tuning
    private static final int EXPELLED_DURATION = 60; // 3 seconds
    private static final int EXPELLED_AMPLIFIER = 0; // always low level

    // Radius
    private static final double BURST_RADIUS = 5.0;

    // Knockback
    private static final float KB_BASE = 0.6f;          // minimum push
    private static final float KB_PER_STORED = 0.07f;   // scaling per absorbed point
    private static final float KB_MAX = 2.2f;           // cap
    private static final float KB_VERTICAL = 0.35f;     //  lift
    private static final float KB_MIN_FALLOFF = 0.2f;   // prevent knockback at edge

    // visual
    private static final int VFX_BASE_PARTICLES = 12;
    private static final int VFX_PARTICLES_PER_STORED = 1;
    private static final int VFX_MAX_PARTICLES = 50;

    private static final float VFX_SHINE_THRESHOLD = 8.0f;
    private static final float VFX_SUPER_SHINE_THRESHOLD = 16.0f;

    private static final int EXPELLED_PARTICLES = 6;

    @Override
    public void activateSecondary(ServerPlayer player) {
        HealingState state = getState(player);
        state.absorbStored = 0f;
        state.absorbTicks = ABSORB_DURATION;

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 1.3f);
    }

    private void handleAbsorb(ServerPlayer player, HealingState state) {
        if (state.absorbTicks <= 0) return;

        state.absorbTicks--;
        ServerLevel w = player.serverLevel();

        float capped = Math.min(state.absorbStored, BURST_MAX_SCALING);
        float intensity = capped / BURST_MAX_SCALING; // 0.0 to 1.0 scaling

        int sparkCount = 2 + (int)(intensity * 5); // 2 to 7 sparks per tick

        w.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                player.getX(),
                player.getY() + player.getBbHeight() * 0.5,
                player.getZ(),
                sparkCount,
                0.5, 0.6, 0.5,
                0.02 + (intensity * 0.05) // speed ramps up
        );

        // extra fx when higher charges
        if (intensity > 0.5f && w.getGameTime() % 5 == 0) {
            w.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 1, 0.5, 0.6, 0.5, 0.01);
        }

        if (state.absorbTicks <= 0) {
            releaseBurst(player, state);
            return;
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                10,
                3,
                true, false, true
        ));
    }

    private void releaseBurst(ServerPlayer player, HealingState state) {
        ServerLevel w = player.serverLevel();

        float stored = state.absorbStored;
        float capped = Math.min(stored, BURST_MAX_SCALING);

        // clear stored
        state.absorbStored = 0f;

        // damage
        float burstDamage = BURST_BASE_DAMAGE + (capped * BURST_DAMAGE_PER_STORED);

        // knockback scaling
        float kbStrength = KB_BASE + (capped * KB_PER_STORED);
        if (kbStrength > KB_MAX) kbStrength = KB_MAX;

        double radius = BURST_RADIUS;

        // VISUAL SCALING

        int particleCount = VFX_BASE_PARTICLES + (int)(capped * VFX_PARTICLES_PER_STORED);
        if (particleCount > VFX_MAX_PARTICLES) particleCount = VFX_MAX_PARTICLES;

        // main explosion
        double px = player.getX();
        double py = player.getY() + player.getBbHeight() * 0.5;
        double pz = player.getZ();

        int emitterCount = 1 + (int)(capped * 0.25f); // how many - should be rounded
        if (emitterCount > 6) emitterCount = 6;       // cap
        double spread = 0.5 + (capped * 0.1); // spread

        // main explosion
        for (int i = 0; i < emitterCount; i++) {

            double ox = (w.random.nextDouble() * 2 - 1) * spread;
            double oy = (w.random.nextDouble() * 2 - 1) * spread * 0.6; // less vertical
            double oz = (w.random.nextDouble() * 2 - 1) * spread;

            w.sendParticles(
                    ParticleTypes.EXPLOSION_EMITTER,
                    px + ox,
                    py + oy,
                    pz + oz,
                    1,      // always 1 for emitter
                    0, 0, 0,
                    0
            );

            w.sendParticles(
                    HEAL_DUST,
                    px + ox,
                    py + oy,
                    pz + oz,
                    15,
                    0.4, 0.4, 0.4,
                    0.05
            );
        }

        // mid-tier shine
        if (capped >= VFX_SHINE_THRESHOLD) {
            spawnBurst(w, px, py, pz,
                    40,
                    0.25,
                    0.8,
                    ParticleTypes.END_ROD);
        }

        if (capped >= VFX_SUPER_SHINE_THRESHOLD) {
            spawnBurst(w, px, py, pz,
                    10,
                    0.35,
                    0.8,
                    ParticleTypes.FLASH);

            spawnBurst(w, px, py, pz,
                    20,
                    0.3,
                    0.5,
                    ParticleTypes.TOTEM_OF_UNDYING);
        }

        // DAMAGE + KNOCKBACK
        for (LivingEntity e : w.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                LivingEntity::isAlive)) {

            if (e == player) continue;

            // damage
            e.hurt(ModDamageTypes.absorbpulse(w, player), burstDamage);

            // knockback
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();

            double distSq = dx * dx + dz * dz;

            if (distSq > 0.0001) {
                double dist = Math.sqrt(distSq);

                dx /= dist;
                dz /= dist;

                double falloff = 1.0 - (dist / radius);
                falloff = Math.max(KB_MIN_FALLOFF, falloff);

                double finalKb = kbStrength * falloff;

                e.setDeltaMovement(e.getDeltaMovement().add(
                        dx * finalKb,
                        KB_VERTICAL,
                        dz * finalKb
                ));

                e.hasImpulse = true;
                e.hurtMarked  = true; // explicit sync; don't rely solely on hurt() setting this

                if (e instanceof ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
                }
            }

            // cleanse
            applyAndCleanse(player, e);
        }

        //SOUND SCALING
        float pitch = 0.9f + (capped * 0.02f);
        if (pitch > 1.5f) pitch = 1.5f;

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(),
                1.0f, pitch);

        CameraShake.shakeNearby(player, 6.0, 10, 0.7f);
    }

    private void applyAndCleanse(ServerPlayer player, LivingEntity target) {
        ServerLevel w = player.serverLevel();

        // fetch active effects safely
        java.util.List<MobEffectInstance> effects =
                new java.util.ArrayList<>(player.getActiveEffects());

        for (MobEffectInstance effect : effects) {
            // only take negative effects (catch all categories just in case)
            if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) continue;

            // apply weakened version
            target.addEffect(new MobEffectInstance(
                    effect.getEffect(),
                    EXPELLED_DURATION,
                    EXPELLED_AMPLIFIER,
                    true, false, true
            ));

            // remove from player
            player.removeEffect(effect.getEffect());

            // more visuals
            double dx = target.getX() - player.getX();
            double dy = target.getY() + target.getBbHeight() * 0.5 - (player.getY() + player.getBbHeight() * 0.5);
            double dz = target.getZ() - player.getZ();

            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0.0001) {
                dx /= len;
                dy /= len;
                dz /= len;

                for (int i = 0; i < EXPELLED_PARTICLES; i++) {
                    w.sendParticles(
                            ParticleTypes.SMOKE,
                            player.getX(),
                            player.getY() + player.getBbHeight() * 0.5,
                            player.getZ(),
                            0,
                            dx * 0.4,
                            dy * 0.4,
                            dz * 0.4,
                            1.0
                    );
                }
            }
        }

        // also treat fire like an effect
        if (player.isOnFire()) {
            player.clearFire();

            target.igniteForSeconds(2);

            w.sendParticles(
                    ParticleTypes.FLAME,
                    player.getX(),
                    player.getY() + player.getBbHeight() * 0.5,
                    player.getZ(),
                    8,
                    0.3, 0.4, 0.3,
                    0.02
            );
        }
    }

    private void spawnBurst(ServerLevel w,
                            double x, double y, double z,
                            int count,
                            double speed,
                            double spread,
                            net.minecraft.core.particles.ParticleOptions particle) {

        for (int i = 0; i < count; i++) {

            // random direction (biased outward)
            double dx = w.random.nextGaussian();
            double dy = w.random.nextGaussian() * 0.6; // less vertical clustering
            double dz = w.random.nextGaussian();

            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.0001) continue;

            dx /= len;
            dy /= len;
            dz /= len;

            // offset
            double distance = spread * (0.5 + w.random.nextDouble()); // should avoid clustering

            double px = x + dx * distance;
            double py = y + dy * distance;
            double pz = z + dz * distance;

            // velocity variation below:
            double velocityScale = speed * (0.8 + w.random.nextDouble() * 0.7);

            double vx = dx * velocityScale;
            double vy = dy * velocityScale;
            double vz = dz * velocityScale;

            w.sendParticles(
                    particle,
                    px, py, pz,
                    0,
                    vx, vy, vz,
                    1.0
            );
        }
    }

    /* ============================================================
       ULTIMATE - ADAPTIVE SURVIVABILITY
       ============================================================ */

    private static final int ULT_DURATION = 250;

    private static final int EFFECT_REFRESH = 30;

    private static final float MEDIC_SOUND_CHANCE = 0.05f;

    // lifesteal scaling
    private static final float LS_HIGH = 0.35f;
    private static final float LS_MID = 0.25f;

    // smoothing (damage reduction %)
    private static final float SMOOTH_MID = 0.15f;
    private static final float SMOOTH_LOW = 0.25f;

    // cleansing interval
    private static final int CLEANSE_INTERVAL = 20;

    // 5 = 100%, 1 = 20%
    private int getPhase(float hp) {
        if (hp >= 1.0f) return 5;     // Overflow
        if (hp >= 0.8f) return 4;     // Exceptional
        if (hp >= 0.6f) return 3;     // Equilibrium
        if (hp >= 0.4f) return 2;     // Inadequate
        return 1;                     // Exposed
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        HealingState state = getState(player);
        state.ultTicks = ULT_DURATION;
        state.ultPhase = -1; // Force immediate phase calculation
    }

    private void handleUltimate(ServerPlayer player, HealingState state) {
        if (state.ultTicks <= 0) return;

        state.ultTicks--;
        ServerLevel w = player.serverLevel();

        // Ult just expired — clean up and exit before sending any FX
        if (state.ultTicks <= 0) {
            state.lifesteal = 0f;
            state.smoothing = 0f;
            state.ultPhase = -1;
            return;
        }

        // Phase detection (done before FX dispatch so the payload carries the right phase)
        float hpPercent = player.getHealth() / player.getMaxHealth();
        int phase = getPhase(hpPercent);
        int prevPhase = state.ultPhase;
        boolean isPhaseChange = (phase != prevPhase);

        if (isPhaseChange) {
            state.ultPhase = phase;

            if (phase == 1) { // EXPOSED ENTRY
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        40,
                        2,
                        true, false, true
                ));

                net.minecraft.sounds.SoundEvent sound = w.random.nextFloat() < MEDIC_SOUND_CHANCE
                        ? ModSounds.MEDIC.get()
                        : SoundEvents.ENDER_DRAGON_FLAP;

                w.playSound(null, player.getX(), player.getY(), player.getZ(),
                        sound,
                        player.getSoundSource(),
                        0.9f, 1.0f);

                CameraShake.shakeNearby(player, 6.0, 13, 0.7f);
            }
        }

        // Dispatch all particle FX to clients
        spawnHealingUltFx(player, phase, state.ultTicks, isPhaseChange);

        applyPhaseEffects(player, state, phase, state.ultTicks);
    }

    /** Sends the healing ult FX payload to the caster and all nearby observers. */
    private static void spawnHealingUltFx(ServerPlayer player, int phase, int ultTicks, boolean isPhaseChange) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new HealingUltPayload(player.getId(), phase, ultTicks, isPhaseChange)
        );
    }

    private void applyPhaseEffects(ServerPlayer player, HealingState state, int phase, int ticks) {

        switch (phase) {
            case 5: // OVERFLOW
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, EFFECT_REFRESH, 1, true, false, true
                ));

                player.addEffect(new MobEffectInstance(
                        MobEffects.ABSORPTION, EFFECT_REFRESH, 1, true, false, true
                ));
                break;

            case 4: // EXCEPTIONAL
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, EFFECT_REFRESH, 0, true, false, true
                ));

                state.lifesteal = LS_HIGH;
                break;

            case 3: // EQUILIBRIUM
                player.addEffect(new MobEffectInstance(
                        MobEffects.REGENERATION, EFFECT_REFRESH, 0, true, false, true
                ));

                state.lifesteal = LS_MID;
                state.smoothing = SMOOTH_MID;
                break;

            case 2: // INADEQUATE
                player.addEffect(new MobEffectInstance(
                        MobEffects.REGENERATION, EFFECT_REFRESH, 1, true, false, true
                ));

                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, EFFECT_REFRESH, 0, true, false, true
                ));

                state.smoothing = SMOOTH_LOW;
                tryCleanse(player, ticks, 10);
                break;

            case 1: // EXPOSED
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, EFFECT_REFRESH, 1, true, false, true
                ));

                player.addEffect(new MobEffectInstance(
                        MobEffects.REGENERATION, EFFECT_REFRESH, 1, true, false, true
                ));

                state.smoothing = SMOOTH_LOW;
                tryCleanse(player, ticks, 5);
                break;
        }
    }

    private void tryCleanse(ServerPlayer player, int ticks, int interval) {
        if (ticks % interval != 0) return;

        // safe checking using category instead of isBeneficial
        for (Holder<MobEffect> effectType : new java.util.ArrayList<>(player.getActiveEffectsMap().keySet())) {
            if (effectType.value().getCategory() != MobEffectCategory.BENEFICIAL) {
                player.removeEffect(effectType);
                break;
            }
        }
    }

    /* ============================================================
       OTHER STUFF
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.healing.name").getString(); }
    @Override public String getPassiveName() { return Component.translatable("power.loopypowers.healing.passive_name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.healing.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.healing.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.healing.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs() { return 33_000; }
    @Override public long getSecondaryCooldownMs() { return 29_000; }
    @Override public long getUltimateCooldownMs() { return 350_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.healing.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.healing.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.healing.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.healing.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.healing.description.ultimate").getString();
    }
}