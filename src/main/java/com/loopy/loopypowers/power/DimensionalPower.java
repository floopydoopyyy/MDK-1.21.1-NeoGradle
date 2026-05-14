package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.DisplaceEntity;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.RenderPackets;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.*;

public class DimensionalPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, DimensionalState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> ACTIVE_DISPLACEMENTS = new HashMap<>();
    private static final Map<UUID, FractureState> ACTIVE_FRACTURES = new HashMap<>();

    private static class DimensionalState {
        double phaseChance = BASE_PHASE_CHANCE;
        int passiveCdTicks = 0;
        int phaseTicks = 0;
        int immuneTicks = 0;
        int phaseShiftTicks = 0;
        GameType prevMode = GameType.SURVIVAL; // net.minecraft.world.GameMode -> net.minecraft.world.level.GameType
    }

    private DimensionalState getState(ServerPlayer player) {
        return PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new DimensionalState()); // getUuid -> getUUID
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int PASSIVE_PHASE_TICKS = 40;
    private static final int PASSIVE_COOLDOWN    = 50;

    // PASSIVE
    private static final double BASE_PHASE_CHANCE        = 0.0005;
    private static final double DAMAGE_CHANCE_GAIN       = 0.002;
    private static final double MAX_PHASE_CHANCE         = 0.25;
    private static final int    FLICKER_CYCLE            = 8;
    private static final int    FLICKER_ON_TIME          = 3;

    private static final float SITUATIONAL_DIMENSION_CHANCE = 0.25f;
    private static final float SILLY_EXIT_CHANCE            = 0.03f;

    // PRIMARY
    private static final int PHASE_SHIFT_DURATION = 55;

    private static final float  ENTRY_SOUND_VOL   = 0.7f;
    private static final float  ENTRY_SOUND_PITCH  = 1.3f;
    private static final float  EXIT_SOUND_VOL    = 0.9f;
    private static final float  EXIT_SOUND_PITCH   = 0.8f;

    private static final int    TRAIL_INTERVAL           = 3;
    private static final int    EXIT_BURST_COUNT_MULT    = 2;
    private static final double EXIT_BURST_SPREAD        = 1.2;
    private static final double EXIT_BURST_SPEED         = 0.08;

    private static final double EXIT_DAMAGE_RADIUS       = 3.5;
    private static final float  EXIT_DAMAGE              = 16.5f;
    private static final double EXIT_PULL_STRENGTH       = 0.45;
    private static final double EXIT_KNOCKBACK_STRENGTH  = 0.8;
    private static final double EXIT_VERTICAL_BOOST      = 0.15;

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("int_")); // getCommandTags -> getTags
        PLAYER_STATES.remove(player.getUUID()); // getUuid -> getUUID
    }

    @Override
    public void onRemove(ServerPlayer player) {
        DimensionalState state = PLAYER_STATES.remove(player.getUUID());

        // Return them to survival if they were trapped in spectator mode by the ability
        // player.interactionManager.getGameMode() -> player.gameMode.getGameModeForPlayer()
        // player.changeGameMode -> player.setGameMode, GameMode -> GameType
        if (state != null && state.phaseShiftTicks > 0 && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setGameMode(state.prevMode);
        }

        player.getTags().removeIf(tag -> tag.startsWith("int_"));

        // Clear active ultimate fractures to prevent permanent slow zones
        ACTIVE_FRACTURES.remove(player.getUUID());

        // Sweep for entities that were displaced by the secondary
        Map<UUID, Integer> myDisplacements = ACTIVE_DISPLACEMENTS.remove(player.getUUID());
        if (myDisplacements != null && player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) { // getWorlds -> getAllLevels
                for (UUID targetId : myDisplacements.keySet()) {
                    Entity e = w.getEntity(targetId);
                    if (e instanceof LivingEntity le) {
                        // removeStatusEffect -> removeEffect, StatusEffects -> MobEffects
                        le.removeEffect(MobEffects.INVISIBILITY);
                        le.removeEffect(MobEffects.WEAKNESS);
                        le.removeEffect(MobEffects.DAMAGE_RESISTANCE);    // StatusEffects.RESISTANCE
                        le.removeEffect(MobEffects.DIG_SLOWDOWN);          // StatusEffects.MINING_FATIGUE
                        if (le instanceof Mob mob) mob.setNoAi(false);    // MobEntity -> Mob, setAiDisabled -> setNoAi
                    }
                }

                // Free entities stuck inside the ultimate
                // getBoundingBox().expand -> getBoundingBox().inflate, getEntitiesByClass -> getEntitiesOfClass
                // Registries.STATUS_EFFECT.getEntry(ModEffects.X) -> ModEffects.X (DeferredHolder passed directly)
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(150), LivingEntity::isAlive)) {
                    e.removeEffect(ModEffects.FRACTURED);
                    e.removeEffect(ModEffects.DISPLACED);
                }
            }
        }
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        ServerLevel world = player.serverLevel(); // getServerWorld -> serverLevel
        DimensionalState state = getState(player);

        handlePassive(player, state, world);
        handlePhaseShift(player, state, world);
        handleFracture(player); // ultimate tick

        // Process displacements owned by this specific player without scanning the world
        Map<UUID, Integer> displaced = ACTIVE_DISPLACEMENTS.get(player.getUUID());
        if (displaced != null && !displaced.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = displaced.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                Entity ent = world.getEntity(entry.getKey());

                if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
                    it.remove();
                    continue;
                }

                int ticksLeft = entry.getValue() - 1;
                if (ticksLeft <= 0) {
                    if (target instanceof Mob mob) mob.setNoAi(false); // MobEntity -> Mob, setAiDisabled -> setNoAi
                    it.remove();
                } else {
                    entry.setValue(ticksLeft);
                    tickDisplaceTarget(target, world);
                }
            }
        }
    }

    @Override
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        DimensionalState state = getState(victim);

        // Fast fail if immune from passive
        if (state.immuneTicks > 0) return false;

        // Increase phase chance on hit
        state.phaseChance = Math.min(MAX_PHASE_CHANCE, state.phaseChance + DAMAGE_CHANCE_GAIN);
        return true;
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void handlePassive(ServerPlayer player, DimensionalState state, ServerLevel world) {
        if (state.phaseShiftTicks > 0) return;
        if (!PassiveManager.isEnabled(player)) return;

        // Handle flicker first if phasing
        if (state.phaseTicks > 0) {
            handleFlicker(player, state, world);
            return;
        }

        // Then tick cooldown
        if (state.passiveCdTicks > 0) {
            state.passiveCdTicks--;
            return;
        }

        // Roll
        if (world.random.nextDouble() < state.phaseChance) {
            startPassivePhase(player, state);

            // Reset chance after proc
            state.phaseChance = BASE_PHASE_CHANCE;

            // Start cooldown
            state.passiveCdTicks = PASSIVE_COOLDOWN;
        }
    }

    private void startPassivePhase(ServerPlayer player, DimensionalState state) {
        state.phaseTicks = PASSIVE_PHASE_TICKS;

        // Registries.SOUND_EVENT.getEntry(ModSounds.X) -> ModSounds.X.get() (DeferredHolder)
        // getServerWorld -> serverLevel, getSoundCategory -> getSoundSource
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FLICKER.get(),
                player.getSoundSource(), 0.5f, 1.2f);
    }

    private void handleFlicker(ServerPlayer player, DimensionalState state, ServerLevel world) {

        long time = world.getGameTime(); // getTime -> getGameTime

        int cyclePos = (int)(time % FLICKER_CYCLE);
        boolean flickerActive = cyclePos < FLICKER_ON_TIME;

        // particles
        if (cyclePos == 0) {

            // DustParticleEffect -> DustParticleOptions
            DustParticleOptions darkBlue  = new DustParticleOptions(new Vector3f(0.05f, 0.1f,  0.4f),  1.4f);
            DustParticleOptions midBlue   = new DustParticleOptions(new Vector3f(0.2f,  0.4f,  1.0f),  1.2f);
            DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f, 0.8f,  1.0f),  0.9f);

            double x = player.getX();
            double y = player.getY() + player.getBbHeight() * 0.5; // getBodyY(0.5) -> getY() + getBbHeight() * 0.5
            double z = player.getZ();

            // spawnParticles -> sendParticles
            world.sendParticles(brightBlue, x, y, z, 12, 0.5, 0.7, 0.5, 0.03);
            world.sendParticles(midBlue,    x, y, z, 8,  0.35, 0.55, 0.35, 0.02);

            if (world.random.nextFloat() < 0.4f) {
                world.sendParticles(darkBlue, x, y, z, 3, 0.3, 0.4, 0.3, 0.015);
            }

            var flickerSound = switch (world.random.nextInt(3)) {
                case 0  -> ModSounds.FLICKER.get();
                case 1  -> ModSounds.FLICKER2.get();
                default -> ModSounds.FLICKER3.get();
            };

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    flickerSound,
                    player.getSoundSource(), 0.5f, 1.0f);
        }

        // flicker
        if (flickerActive) {
            RenderPackets.hidePlayerFromOthers(player, 1);

            // addStatusEffect -> addEffect, StatusEffects -> MobEffects
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2, 0, true, false));
        }

        // damage immunity
        state.immuneTicks = state.phaseTicks;

        state.phaseTicks--;

        // if the tag just expired and the player is fully returning
        if (state.phaseTicks <= 0) {
            state.immuneTicks = 0;
            triggerSituationalFlicker(player, world);
        }
    }

    private void triggerSituationalFlicker(ServerPlayer player, ServerLevel world) {
        // rng check
        if (world.random.nextFloat() > SITUATIONAL_DIMENSION_CHANCE) return;

        // prioritize fall damage -> drowning -> freezing -> levitation -> blindness -> poison/wither -> starvation -> fire -> slowness
        if (player.fallDistance > 5.0f) {
            // zero-g void
            player.fallDistance = 0;
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));

            // ENTITY_PHANTOM_FLAP -> PHANTOM_FLAP (removed ENTITY_ prefix)
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, player.getSoundSource(), 1.0f, 0.5f);
            world.sendParticles(ParticleTypes.SQUID_INK,          player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
            world.sendParticles(ParticleTypes.POOF,                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);

        } else if (player.getAirSupply() <= 0) { // getAir -> getAirSupply
            // air pocket
            player.setAirSupply(player.getMaxAirSupply()); // setAir/getMaxAir -> setAirSupply/getMaxAirSupply

            // ENTITY_PLAYER_BREATH -> PLAYER_BREATH
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, player.getSoundSource(), 1.0f, 1.0f);
            world.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getEyeY(), player.getZ(), 40, 0.4, 0.4, 0.4, 0.1);
            world.sendParticles(ParticleTypes.CLOUD,      player.getX(), player.getEyeY(), player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);

        } else if (player.getTicksFrozen() > 0) { // getFrozenTicks -> getTicksFrozen
            // fire world
            player.setTicksFrozen(0); // setFrozenTicks -> setTicksFrozen

            // BLOCK_FIRE_EXTINGUISH -> FIRE_EXTINGUISH
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, player.getSoundSource(), 0.8f, 1.0f);
            world.sendParticles(ParticleTypes.LAVA,                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);

        } else if (player.hasEffect(MobEffects.LEVITATION)) { // hasStatusEffect -> hasEffect
            // heavy gravity world
            player.removeEffect(MobEffects.LEVITATION); // removeStatusEffect -> removeEffect
            // getVelocity -> getDeltaMovement, setVelocity -> setDeltaMovement
            player.setDeltaMovement(player.getDeltaMovement().x, -1.5, player.getDeltaMovement().z);
            player.hasImpulse = true; // velocityModified -> hasImpulse

            // BLOCK_ANVIL_LAND -> ANVIL_LAND
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, player.getSoundSource(), 0.8f, 0.8f);
            world.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 40, 0.4, 0.6, 0.4, 0.1);

        } else if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS)) {
            // light world
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.DARKNESS);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100, 0, false, false, false));

            // BLOCK_BEACON_ACTIVATE -> BEACON_ACTIVATE
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, player.getSoundSource(), 1.0f, 1.5f);
            world.sendParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);

        } else if (player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER)) {
            // idk cleanse
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);

            // BLOCK_AMETHYST_BLOCK_CHIME -> AMETHYST_BLOCK_CHIME
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.2f, 1.0f);
            world.sendParticles(ParticleTypes.GLOW,   player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 40, 0.4, 0.6, 0.4, 0.1);
            world.sendParticles(ParticleTypes.SCRAPE, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 20, 0.4, 0.6, 0.4, 0.1);

        } else if (player.getFoodData().getFoodLevel() <= 6) { // getHungerManager().getFoodLevel -> getFoodData().getFoodLevel
            // food world
            player.getFoodData().setFoodLevel(8);         // getHungerManager().setFoodLevel -> getFoodData().setFoodLevel
            player.getFoodData().setSaturation(4.0f); // getHungerManager().setSaturationLevel -> getFoodData().setSaturation

            // ENTITY_PLAYER_BURP -> PLAYER_BURP
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, player.getSoundSource(), 1.0f, 0.9f);
            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getEyeY(), player.getZ(), 15, 0.2, 0.2, 0.2, 0.05);

        } else if (player.isOnFire()) {
            // water dimension
            player.clearFire(); // extinguish -> clearFire

            // ENTITY_GENERIC_SPLASH -> GENERIC_SPLASH, BLOCK_FIRE_EXTINGUISH -> FIRE_EXTINGUISH
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH,  player.getSoundSource(), 1.0f, 1.2f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, player.getSoundSource(), 0.6f, 1.0f);

            world.sendParticles(ParticleTypes.SPLASH,        player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 50, 0.4, 0.6, 0.4, 0.1);
            world.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);

        } else if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) { // StatusEffects.SLOWNESS -> MobEffects.MOVEMENT_SLOWDOWN
            // speed world
            MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN); // getStatusEffect -> getEffect
            if (slowness != null && slowness.getAmplifier() >= 1) { // only trigger on slowness II or worse
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false, false)); // StatusEffects.SPEED -> MobEffects.MOVEMENT_SPEED

                // ENTITY_MINECART_RIDING -> MINECART_RIDING
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MINECART_RIDING, player.getSoundSource(), 0.5f, 1.5f);
                world.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + player.getBbHeight() * 0.2, player.getZ(), 25, 0.3, 0.1, 0.3, 0.1);
            }
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        DimensionalState state = getState(player);

        // clear passive
        state.phaseTicks = 0;
        state.immuneTicks = 0;

        state.phaseShiftTicks = PHASE_SHIFT_DURATION;
        // player.interactionManager.getGameMode() -> player.gameMode.getGameModeForPlayer()
        state.prevMode = player.gameMode.getGameModeForPlayer();

        // player.changeGameMode -> player.setGameMode, GameMode.SPECTATOR -> GameType.SPECTATOR
        player.setGameMode(GameType.SPECTATOR);

        spawnPhaseParticles(world, player);

        // ENTITY_ENDERMAN_TELEPORT -> ENDERMAN_TELEPORT, getSoundCategory -> getSoundSource
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT,
                player.getSoundSource(), ENTRY_SOUND_VOL, ENTRY_SOUND_PITCH);
    }

    private void handlePhaseShift(ServerPlayer player, DimensionalState state, ServerLevel world) {
        if (state.phaseShiftTicks <= 0) return;

        state.phaseShiftTicks--;

        if (state.phaseShiftTicks <= 0) {
            // if ended, exit
            // player.interactionManager.getGameMode() == GameMode.SPECTATOR -> player.isSpectator()
            if (player.isSpectator()) {
                exitPhaseShift(player, state, world);
            }
            return;
        }

        // trail particles
        if (world.getGameTime() % TRAIL_INTERVAL == 0) { // getTime -> getGameTime

            DustParticleOptions midBlue = new DustParticleOptions(new Vector3f(0.2f, 0.4f, 1.0f), 1.0f);

            world.sendParticles(midBlue,
                    player.getX(),
                    player.getY() + player.getBbHeight() * 0.5,
                    player.getZ(),
                    2,
                    0.15, 0.2, 0.15,
                    0.01
            );
        }
    }

    private void exitPhaseShift(ServerPlayer player, DimensionalState state, ServerLevel world) {

        // back to previous gamemode
        player.setGameMode(state.prevMode);

        // ENTITY_WARDEN_SONIC_BOOM -> WARDEN_SONIC_BOOM
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM,
                player.getSoundSource(), EXIT_SOUND_VOL, EXIT_SOUND_PITCH);

        // damage nearby entities
        // getBoundingBox().expand -> getBoundingBox().inflate, getEntitiesByClass -> getEntitiesOfClass
        for (LivingEntity e : world.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(EXIT_DAMAGE_RADIUS),
                en -> en.isAlive() && en != player)) {

            // damage -> hurt
            e.hurt(ModDamageTypes.phaseBurst(world, player), EXIT_DAMAGE);

            // direction vectors
            // getPos -> position
            Vec3 fromPlayer = e.position().subtract(player.position());
            double distance = fromPlayer.length();

            if (distance < 0.001) continue;

            Vec3 dir = fromPlayer.normalize();

            // Vec3d.multiply -> Vec3.scale
            Vec3 pull      = dir.scale(-EXIT_PULL_STRENGTH);
            Vec3 knockback = dir.scale(EXIT_KNOCKBACK_STRENGTH);

            Vec3 finalVelocity = pull.add(knockback).add(0, EXIT_VERTICAL_BOOST, 0);

            // addVelocity -> push (accumulates onto existing motion, same as addVelocity)
            e.push(finalVelocity.x, finalVelocity.y, finalVelocity.z);
            e.hasImpulse = true; // velocityModified -> hasImpulse
        }

        // exit particles
        DustParticleOptions darkBlue   = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f), 1.4f);
        DustParticleOptions midBlue    = new DustParticleOptions(new Vector3f(0.2f,  0.4f, 1.0f), 1.2f);
        DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f,  0.8f, 1.0f), 1.1f);

        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.5;
        double z = player.getZ();

        world.sendParticles(brightBlue,
                x, y, z,
                25 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD, EXIT_BURST_SPREAD, EXIT_BURST_SPREAD,
                EXIT_BURST_SPEED);

        world.sendParticles(midBlue,
                x, y, z,
                18 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD * 0.8, EXIT_BURST_SPREAD * 0.8, EXIT_BURST_SPREAD * 0.8,
                EXIT_BURST_SPEED * 0.8);

        world.sendParticles(darkBlue,
                x, y, z,
                8 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD * 0.6, EXIT_BURST_SPREAD * 0.6, EXIT_BURST_SPREAD * 0.6,
                EXIT_BURST_SPEED * 0.6);

        CameraShake.shakeNearby(player, 5, 10, 0.6f);

        // -- easter egg --
        if (world.random.nextFloat() < SILLY_EXIT_CHANCE) {
            int gag = world.random.nextInt(9);
            switch (gag) {
                case 0 -> {
                    // bring a strider
                    // net.minecraft.entity.passive.StriderEntity -> net.minecraft.world.entity.monster.Strider
                    // EntityType.X.create(world) stays the same, but world is now ServerLevel
                    // refreshPositionAndAngles -> moveTo
                    Strider strider = EntityType.STRIDER.create(world);
                    if (strider != null) {
                        strider.moveTo(x, y, z, world.random.nextFloat() * 360f, 0);
                        world.addFreshEntity(strider); // spawnEntity -> addFreshEntity
                        world.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 1 -> {
                    // bring a cat
                    // net.minecraft.entity.passive.CatEntity -> net.minecraft.world.entity.animal.Cat
                    Cat cat = EntityType.CAT.create(world);
                    if (cat != null) {
                        cat.moveTo(x, y, z, world.random.nextFloat() * 360f, 0);
                        world.addFreshEntity(cat);
                        world.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 2 -> {
                    // bring hamuel
                    // net.minecraft.entity.passive.PigEntity -> net.minecraft.world.entity.animal.Pig
                    // net.minecraft.text.Text.translatable -> Component.translatable
                    Pig pig = EntityType.PIG.create(world);
                    if (pig != null) {
                        pig.moveTo(x, y, z, world.random.nextFloat() * 360f, 0);
                        pig.setCustomName(Component.translatable("entity.loopypowers.hamuel"));
                        world.addFreshEntity(pig);
                        world.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 3 -> {
                    // bring woolliam
                    // net.minecraft.entity.passive.SheepEntity -> net.minecraft.world.entity.animal.Sheep
                    Sheep sheep = EntityType.SHEEP.create(world);
                    if (sheep != null) {
                        sheep.moveTo(x, y, z, world.random.nextFloat() * 360f, 0);
                        sheep.setCustomName(Component.translatable("entity.loopypowers.woolliam"));
                        world.addFreshEntity(sheep);
                        world.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 4 -> {
                    // bring 3 cod
                    // net.minecraft.entity.passive.CodEntity -> net.minecraft.world.entity.animal.Cod
                    for (int c = 0; c < 3; c++) {
                        Cod cod = EntityType.COD.create(world);
                        if (cod != null) {
                            cod.moveTo(x, y, z, world.random.nextFloat() * 360f, 0);
                            world.addFreshEntity(cod);
                        }
                    }
                    world.sendParticles(ParticleTypes.SPLASH, x, y + 0.5, z, 15, 0.3, 0.3, 0.3, 0.05);
                }
                case 5 -> {
                    // any leather armour being put on an empty slot
                    // net.minecraft.entity.EquipmentSlot -> net.minecraft.world.entity.EquipmentSlot
                    // net.minecraft.item.Items -> net.minecraft.world.item.Items
                    EquipmentSlot[] slots = {
                            EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST,
                            EquipmentSlot.LEGS,
                            EquipmentSlot.FEET
                    };
                    net.minecraft.world.item.Item[] leathers = {
                            Items.LEATHER_HELMET,
                            Items.LEATHER_CHESTPLATE,
                            Items.LEATHER_LEGGINGS,
                            Items.LEATHER_BOOTS
                    };

                    int startIdx = world.random.nextInt(4);
                    for (int i = 0; i < 4; i++) {
                        int idx = (startIdx + i) % 4;
                        // getEquippedStack -> getItemBySlot, equipStack -> setItemSlot
                        if (player.getItemBySlot(slots[idx]).isEmpty()) {
                            player.setItemSlot(slots[idx], new ItemStack(leathers[idx]));
                            // ITEM_ARMOR_EQUIP_LEATHER -> ARMOR_EQUIP_LEATHER
                            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, player.getSoundSource(), 1.0f, 1.0f);
                            break;
                        }
                    }
                }
                case 6 -> {
                    // a steve head appearing on head slot
                    if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
                        // ENTITY_ITEM_PICKUP -> ITEM_PICKUP
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, player.getSoundSource(), 1.0f, 1.0f);
                    }
                }
                case 7 -> {
                    // a few snowballs being shot out
                    // net.minecraft.entity.projectile.thrown.SnowballEntity -> net.minecraft.world.entity.projectile.Snowball
                    int balls = 3 + world.random.nextInt(3);
                    for (int i = 0; i < balls; i++) {
                        Snowball snowball = new Snowball(world, player);
                        snowball.setPos(x, y + 1.0, z);
                        // setVelocity -> setDeltaMovement
                        snowball.setDeltaMovement(
                                (world.random.nextDouble() - 0.5) * 1.5,
                                world.random.nextDouble(),
                                (world.random.nextDouble() - 0.5) * 1.5
                        );
                        world.addFreshEntity(snowball);
                    }
                    // ENTITY_SNOWBALL_THROW -> SNOWBALL_THROW
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, player.getSoundSource(), 1.0f, 1.0f);
                }
                case 8 -> {
                    // a few falling sand blocks
                    int sands = 2 + world.random.nextInt(3);
                    for (int i = 0; i < sands; i++) {
                        // net.minecraft.entity.FallingBlockEntity -> net.minecraft.world.entity.item.FallingBlockEntity
                        // FallingBlockEntity.spawnFromBlock -> FallingBlockEntity.fall
                        // blockPos.up(n) -> blockPos.above(n), blockPos.add -> blockPos.offset
                        // Blocks.SAND.getDefaultState() -> Blocks.SAND.defaultBlockState()
                        FallingBlockEntity sand = FallingBlockEntity.fall(
                                world,
                                player.blockPosition().above(2).offset(
                                        world.random.nextInt(2) - 1, 0, world.random.nextInt(2) - 1),
                                Blocks.SAND.defaultBlockState()
                        );
                        sand.setDeltaMovement(
                                (world.random.nextDouble() - 0.5) * 0.4,
                                0.2,
                                (world.random.nextDouble() - 0.5) * 0.4
                        );
                    }
                }
            }
        }
    }

    private void spawnPhaseParticles(ServerLevel world, ServerPlayer player) {

        DustParticleOptions darkBlue   = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f), 1.4f);
        DustParticleOptions midBlue    = new DustParticleOptions(new Vector3f(0.2f,  0.4f, 1.0f), 1.2f);
        DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f,  0.8f, 1.0f), 1.0f);

        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.5;
        double z = player.getZ();

        world.sendParticles(brightBlue, x, y, z, 15, 0.6, 0.8, 0.6, 0.04);
        world.sendParticles(midBlue,    x, y, z, 10, 0.4, 0.6, 0.4, 0.03);

        if (world.random.nextFloat() < 0.5f) {
            world.sendParticles(darkBlue, x, y, z, 4, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        // getRotationVec -> getViewVector
        Vec3 look = player.getViewVector(1.0f);

        // ModEntities.DISPLACE_ENTITY -> ModEntities.DISPLACE_ENTITY.get() (DeferredHolder)
        DisplaceEntity bolt = new DisplaceEntity(ModEntities.DISPLACE_ENTITY.get(), world);

        bolt.setOwner(player);

        bolt.setPos(
                player.getX(),
                player.getEyeY() - 0.1,
                player.getZ()
        );

        // setVelocity + Vec3d.multiply -> setDeltaMovement + Vec3.scale
        bolt.setDeltaMovement(look.scale(0.6));

        world.addFreshEntity(bolt); // spawnEntity -> addFreshEntity

        player.swing(InteractionHand.MAIN_HAND, true); // swingHand -> swing

        // ENTITY_ENDERMAN_TELEPORT -> ENDERMAN_TELEPORT
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT,
                player.getSoundSource(), 0.6f, 1.4f);
    }

    // Suppressed warning since this is called remotely via the projectile entity
    @SuppressWarnings("unused")
    public static void applyDisplace(ServerPlayer caster, LivingEntity target, int durationTicks) {
        ACTIVE_DISPLACEMENTS.computeIfAbsent(caster.getUUID(), k -> new HashMap<>()).put(target.getUUID(), durationTicks);
    }

    private void tickDisplaceTarget(LivingEntity entity, ServerLevel world) {
        // setVelocity -> setDeltaMovement, Vec3d.ZERO -> Vec3.ZERO
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true; // velocityModified -> hasImpulse
        entity.fallDistance = 0;

        // Lock position using packets
        if (entity instanceof ServerPlayer displacedPlayer) {
            // networkHandler -> connection, sendPacket -> send
            // PlayerPositionLookS2CPacket -> ClientboundPlayerPositionPacket
            // getYaw -> getYRot, getPitch -> getXRot
            // PositionFlag -> RelativeMovement (net.minecraft.world.entity.RelativeMovement)
            displacedPlayer.connection.send(
                    new ClientboundPlayerPositionPacket(
                            displacedPlayer.getX(),
                            displacedPlayer.getY(),
                            displacedPlayer.getZ(),
                            displacedPlayer.getYRot(),
                            displacedPlayer.getXRot(),
                            Set.of(
                                    RelativeMovement.X_ROT,
                                    RelativeMovement.Y_ROT
                            ),
                            0
                    )
            );

            // stop mining — addStatusEffect -> addEffect, StatusEffects -> MobEffects
            // StatusEffects.MINING_FATIGUE -> MobEffects.DIG_SLOWDOWN
            displacedPlayer.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 5, 255, true, false, false));

            // stop actions — getMainHandStack -> getMainHandItem, getItemCooldownManager().set -> getCooldowns().addCooldown
            if (!displacedPlayer.getMainHandItem().isEmpty()) {
                displacedPlayer.getCooldowns().addCooldown(displacedPlayer.getMainHandItem().getItem(), 5);
            }
            displacedPlayer.stopUsingItem();
        }

        // Invisibility
        entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,    5, 0,   true, false, false));
        // stop damage — StatusEffects.RESISTANCE -> MobEffects.DAMAGE_RESISTANCE
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,         5, 255, true, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 255, true, false, false));

        // disable mob ai — MobEntity -> Mob, setAiDisabled -> setNoAi
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }

        // fx
        DustParticleOptions darkBlue   = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f),  1.4f);
        DustParticleOptions midBlue    = new DustParticleOptions(new Vector3f(0.2f,  0.4f, 1.0f),  1.2f);
        DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f,  0.8f, 1.0f),  0.9f);

        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();

        world.sendParticles(brightBlue, x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
        world.sendParticles(midBlue,    x, y, z, 4, 0.35, 0.55, 0.35, 0.02);

        if (world.random.nextFloat() < 0.4f) {
            world.sendParticles(darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    // ── Ultimate — Reality Fracture ─────────────────────────────
    private static final int    ULT_DURATION             = 300;
    private static final double ULT_RIFT_RADIUS          = 26.0;
    private static final int    ULT_CRACK_COUNT          = 15;
    private static final int    ULT_CRACK_SEGMENTS       = 8;
    private static final double ULT_CRACK_SEGMENT_LENGTH = 2.3;
    private static final double ULT_CRACK_JAGGED_ANGLE   = 0.6;
    private static final double ULT_CRACK_WIDTH          = 0.9;
    private static final double ULT_WALL_PARTICLE_HEIGHT  = 3.0;
    private static final int    ULT_WALL_PARTICLE_DENSITY = 1;
    private static final int    ULT_PARTICLE_INTERVAL    = 8;
    private static final int    ULT_RING_COUNT           = 2;
    private static final double ULT_RING_SPACING         = 9.0;
    private static final int    ULT_RING_POINTS          = 20;
    private static final double ULT_RING_WIDTH           = 1.0;
    private static final double MAX_STEP_UP   = 1.25;
    private static final double MAX_STEP_DOWN = 2.5;
    private static final int    SEARCH_DOWN   = 6;
    private static final int    SEARCH_UP     = 2;

    private static class FractureState {
        List<List<Vec3>> cracks = new ArrayList<>();  // Vec3d -> Vec3
        List<List<Vec3>> rings  = new ArrayList<>();
        Vec3 origin;
        int ticksRemaining;
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        // getRotationVec -> getViewVector, getPos -> position
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 origin = player.position().add(look.x * 3.0, 0, look.z * 3.0);

        FractureState state = new FractureState();
        state.origin = origin;
        state.ticksRemaining = ULT_DURATION;

        Random rng = new Random();
        double angleStep = (Math.PI * 2.0) / ULT_CRACK_COUNT;

        // ── Build radial cracks ─────────────────────────────
        for (int i = 0; i < ULT_CRACK_COUNT; i++) {
            double baseAngle = angleStep * i + (rng.nextDouble() - 0.5) * angleStep * 0.6;
            state.cracks.add(buildCrackLine(origin, baseAngle, rng, world));
        }

        // visuals
        spawnRiftOpeningParticles(world, origin);

        for (int i = 1; i <= ULT_RING_COUNT; i++) {
            double radius = i * ULT_RING_SPACING;
            state.rings.add(buildRing(origin, radius, world));
        }

        ACTIVE_FRACTURES.put(player.getUUID(), state);

        // ENTITY_WARDEN_SONIC_BOOM -> WARDEN_SONIC_BOOM
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM,
                player.getSoundSource(), 1.2f, 0.4f);
    }

    private List<Vec3> buildRing(Vec3 center, double radius, ServerLevel world) {
        List<Vec3> points = new ArrayList<>();

        int smoothPoints = ULT_RING_POINTS * 3;

        for (int i = 0; i < smoothPoints; i++) {
            double angle = (Math.PI * 2.0 * i) / smoothPoints;

            Vec3 p = new Vec3(
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius
            );

            points.add(snapToGround(p, world));
        }

        return points;
    }

    private List<Vec3> buildCrackLine(Vec3 origin, double baseAngle, Random rng, ServerLevel world) {

        List<Vec3> points = new ArrayList<>();

        Vec3 current = origin;
        points.add(snapToGround(current, world));

        double currentAngle = baseAngle;

        for (int seg = 0; seg < ULT_CRACK_SEGMENTS; seg++) {

            currentAngle += (rng.nextDouble() - 0.5) * ULT_CRACK_JAGGED_ANGLE;

            double segLen = ULT_CRACK_SEGMENT_LENGTH * (0.7 + rng.nextDouble() * 0.6);

            Vec3 nextFlat = current.add(
                    Math.cos(currentAngle) * segLen,
                    0,
                    Math.sin(currentAngle) * segLen
            );

            // interpolate between points
            int steps = 3 + rng.nextInt(3);

            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;

                Vec3 interp = new Vec3(
                        current.x + (nextFlat.x - current.x) * t,
                        current.y,
                        current.z + (nextFlat.z - current.z) * t
                );

                points.add(snapToGround(interp, world));
            }

            current = nextFlat;

            // distanceTo is the same in Vec3
            if (current.distanceTo(origin) > ULT_RIFT_RADIUS) break;
        }

        return points;
    }

    private void handleFracture(ServerPlayer player) {
        FractureState state = ACTIVE_FRACTURES.get(player.getUUID());
        if (state == null) return;

        if (state.ticksRemaining <= 0) {
            ACTIVE_FRACTURES.remove(player.getUUID());
            return;
        }

        state.ticksRemaining--;

        ServerLevel world = player.serverLevel();
        long time = world.getGameTime(); // getTime -> getGameTime

        // WALLS
        if (time % ULT_PARTICLE_INTERVAL == 0) {
            for (List<Vec3> crack : state.cracks) {
                spawnCrackWallParticles(world, crack);
            }

            for (List<Vec3> ring : state.rings) {
                spawnCrackWallParticles(world, ring);
            }

            if (state.origin != null) {
                spawnRiftAuraParticles(world, state.origin, time);
            }
        }

        // -- COLLISION --
        // getBoundingBox().expand -> getBoundingBox().inflate, getEntitiesByClass -> getEntitiesOfClass
        // Registries.STATUS_EFFECT.getEntry(ModEffects.FRACTURED) -> ModEffects.FRACTURED (DeferredHolder)
        for (LivingEntity e : world.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(ULT_RIFT_RADIUS + 2),
                en -> en.isAlive() && en != player)) {

            if (isNearAnyCrack(e.position(), state) || isNearAnyRing(e.position(), state)) { // getPos -> position
                e.addEffect(new MobEffectInstance(
                        ModEffects.FRACTURED, 40, 0, false, false, true));
            }
        }
    }

    private boolean isNearAnyCrack(Vec3 pos, FractureState state) {
        for (List<Vec3> crack : state.cracks) {
            for (int i = 0; i < crack.size() - 1; i++) {
                if (distanceToSegment(pos, crack.get(i), crack.get(i + 1)) < ULT_CRACK_WIDTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearAnyRing(Vec3 pos, FractureState state) {
        for (List<Vec3> ring : state.rings) {
            for (int i = 0; i < ring.size(); i++) {
                Vec3 a = ring.get(i);
                Vec3 b = ring.get((i + 1) % ring.size());

                if (distanceToSegment(pos, a, b) < ULT_RING_WIDTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        // Flatten to XZ
        Vec3 pFlat = new Vec3(p.x, 0, p.z);
        Vec3 aFlat = new Vec3(a.x, 0, a.z);
        Vec3 bFlat = new Vec3(b.x, 0, b.z);

        Vec3 ab = bFlat.subtract(aFlat);
        // lengthSquared -> lengthSqr
        double len2 = ab.lengthSqr();
        if (len2 < 0.0001) return pFlat.distanceTo(aFlat);

        // MathHelper.clamp -> Mth.clamp, dotProduct -> dot
        double t = Mth.clamp(pFlat.subtract(aFlat).dot(ab) / len2, 0, 1);
        // Vec3d.multiply -> Vec3.scale
        Vec3 closest = aFlat.add(ab.scale(t));
        return pFlat.distanceTo(closest);
    }

    // particles — DustParticleEffect -> DustParticleOptions
    private static final DustParticleOptions CRACK_DARK   = new DustParticleOptions(new Vector3f(0.05f, 0.15f, 0.55f), 1.8f); // deep blue
    private static final DustParticleOptions CRACK_MID    = new DustParticleOptions(new Vector3f(0.25f, 0.55f, 1.0f),  1.4f); // sky blue
    private static final DustParticleOptions CRACK_BRIGHT = new DustParticleOptions(new Vector3f(0.75f, 0.92f, 1.0f),  1.1f); // icy white-blue
    private static final DustParticleOptions RIFT_WHITE   = new DustParticleOptions(new Vector3f(0.9f,  0.97f, 1.0f),  1.2f); // pure white-blue

    private void spawnCrackWallParticles(ServerLevel world, List<Vec3> crack) {
        for (int i = 0; i < crack.size() - 1; i++) {
            Vec3 a = crack.get(i);
            Vec3 b = crack.get(i + 1);
            int steps = Math.max(1, (int)(a.distanceTo(b) / 0.55));

            for (int s = 0; s <= steps; s++) {
                if (world.random.nextInt(4) != 0) continue;

                double t = (double) s / steps;
                double baseX = a.x + (b.x - a.x) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseZ = a.z + (b.z - a.z) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseY = a.y + (b.y - a.y) * t;

                double wallHeight = ULT_WALL_PARTICLE_HEIGHT * (0.5 + world.random.nextDouble() * 0.5);

                // bottom crack
                world.sendParticles(CRACK_DARK, baseX, baseY + 0.05, baseZ, 1, 0.03, 0.01, 0.03, 0.003);
                world.sendParticles(CRACK_MID,  baseX, baseY + 0.12, baseZ, 1, 0.04, 0.02, 0.04, 0.005);

                // make them rise slightly
                for (int h = 1; h < ULT_WALL_PARTICLE_DENSITY + 2; h++) {
                    double y = baseY + (wallHeight * h / (ULT_WALL_PARTICLE_DENSITY + 2));

                    float heightFraction = (float) h / (ULT_WALL_PARTICLE_DENSITY + 2);
                    DustParticleOptions color = heightFraction < 0.35f ? CRACK_DARK : CRACK_BRIGHT;

                    double driftX = (world.random.nextDouble() - 0.5) * 0.015 * h;
                    double driftZ = (world.random.nextDouble() - 0.5) * 0.015 * h;

                    world.sendParticles(color,
                            baseX + driftX, y, baseZ + driftZ,
                            1, 0.01, 0.02 + heightFraction * 0.03, 0.01,
                            0.003 + heightFraction * 0.006);
                }

                if (world.random.nextFloat() < 0.06f) {
                    world.sendParticles(RIFT_WHITE, baseX, baseY + wallHeight * 0.4, baseZ, 1, 0.06, 0.08, 0.06, 0.02);
                }

                if (world.random.nextFloat() < 0.04f) {
                    world.sendParticles(ParticleTypes.END_ROD,
                            baseX, baseY + 0.1, baseZ,
                            1, (world.random.nextDouble() - 0.5) * 0.04,
                            0.06 + world.random.nextDouble() * 0.06,
                            (world.random.nextDouble() - 0.5) * 0.04,
                            0.01);
                }
            }
        }
    }

    private void spawnRiftOpeningParticles(ServerLevel world, Vec3 pos) {
        for (int ring = 0; ring < 3; ring++) {
            double r = 1.5 + ring * 1.8;
            int ringPoints = 16 + ring * 8;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                world.sendParticles(ring == 0 ? RIFT_WHITE : ring == 1 ? CRACK_BRIGHT : CRACK_MID,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * r,
                        1,
                        -Math.cos(angle) * (0.2 + ring * 0.05),
                        0.04,
                        -Math.sin(angle) * (0.2 + ring * 0.05),
                        0.015);
            }
        }

        world.sendParticles(RIFT_WHITE,   pos.x, pos.y + 0.2, pos.z, 25, 0.4, 0.6, 0.4, 0.09);
        world.sendParticles(CRACK_BRIGHT, pos.x, pos.y + 0.2, pos.z, 18, 0.5, 0.7, 0.5, 0.07);
        world.sendParticles(CRACK_MID,    pos.x, pos.y + 0.1, pos.z, 12, 0.6, 0.4, 0.6, 0.05);
        world.sendParticles(CRACK_DARK,   pos.x, pos.y + 0.1, pos.z, 8,  0.7, 0.3, 0.7, 0.04);

        // beam upward
        for (int h = 0; h < 14; h++) {
            double heightFraction = (double) h / 14;
            DustParticleOptions col = heightFraction < 0.3 ? CRACK_DARK
                    : heightFraction < 0.6 ? CRACK_MID
                    : heightFraction < 0.85 ? CRACK_BRIGHT
                    : RIFT_WHITE;

            world.sendParticles(col,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                    pos.y + h * 0.45,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                    1, 0.04, 0.08, 0.04, 0.025);
        }

        for (int i = 0; i < 16; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * r,
                    pos.y + 0.2,
                    pos.z + Math.sin(a) * r,
                    1,
                    Math.cos(a) * 0.04, 0.12 + world.random.nextDouble() * 0.1,
                    Math.sin(a) * 0.04, 0.02);
        }

        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.5, pos.z, 3, 0.15, 0.1, 0.15, 0);
    }

    private void spawnRiftAuraParticles(ServerLevel world, Vec3 pos, long time) {
        // 2 rings around centre
        for (int ring = 0; ring < 2; ring++) {
            double speed  = ring == 0 ? 0.09 : -0.06;
            double radius = ring == 0 ? 0.7  : 1.1;
            double angle  = time * speed;
            int points    = ring == 0 ? 3    : 5;

            for (int i = 0; i < points; i++) {
                double a = angle + (i * Math.PI * 2.0 / points);
                double r = radius + Math.sin(time * 0.07 + i) * 0.15;

                world.sendParticles(ring == 0 ? RIFT_WHITE : CRACK_BRIGHT,
                        pos.x + Math.cos(a) * r,
                        pos.y + 0.2 + Math.sin(time * 0.05 + i) * 0.12,
                        pos.z + Math.sin(a) * r,
                        1, 0, 0.02, 0, 0.008);
            }
        }

        // occasional star
        if (time % 5 == 0) {
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.2,
                    pos.y + 0.15,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.2,
                    1, 0, 0.07 + world.random.nextDouble() * 0.05, 0, 0.01);
        }

        if (time % 8 == 0) {
            world.sendParticles(RIFT_WHITE, pos.x, pos.y + 0.4, pos.z,
                    1, 0.12, 0.12, 0.12, 0.025);
        }
    }

    private Vec3 snapToGround(Vec3 pos, ServerLevel world) {
        if (world == null) return pos;

        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int baseY = (int) Math.floor(pos.y);

        int bestY = baseY;
        boolean found = false;

        for (int dy = 0; dy <= SEARCH_DOWN; dy++) {
            int y = baseY - dy;

            if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                bestY = y + 1;
                found = true;
                break;
            }
        }

        if (!found) {
            for (int dy = 1; dy <= SEARCH_UP; dy++) {
                int y = baseY + dy;

                if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                    if ((y - baseY) <= MAX_STEP_UP) {
                        bestY = y + 1;
                        found = true;
                    }
                    break;
                }
            }
        }

        if (!found) return pos;

        double newY = bestY + 0.05;

        double delta = newY - pos.y;
        if (delta > MAX_STEP_UP || delta < -MAX_STEP_DOWN) {
            return pos;
        }

        return new Vec3(pos.x, newY, pos.z); // Vec3d -> Vec3
    }

    private boolean isSolidGround(ServerLevel world, int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y, z)).isSolid();
    }

    private boolean isAirAbove(ServerLevel world, int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y + 1, z)).isAir();
    }

    /* ============================================================
       STUFF
       ============================================================ */

    @Override public String getName() { return "power.loopypowers.interdimensional.name"; }

    @Override public String getPassiveName()   { return Component.translatable("power.loopypowers.interdimensional.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Component.translatable("power.loopypowers.interdimensional.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.interdimensional.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Component.translatable("power.loopypowers.interdimensional.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 22_000; }
    @Override public long getSecondaryCooldownMs() { return 17_000; }
    @Override public long getUltimateCooldownMs()  { return 330_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.interdimensional.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.interdimensional.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.interdimensional.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.interdimensional.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.interdimensional.description.ultimate").getString();
    }
}