package com.loopy.loopypowers.power;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.CompelEntity;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.entity.PuppetryEntity;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;

public class PsychicPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, PsychicState> ACTIVE_STATES = new HashMap<>();

    private static class PsychicState {
        int leechCd = 0; // replaces LEECH_CD_TAG on the player
        final Map<UUID, CompelEntry>   compelled = new HashMap<>();
        final Map<UUID, SpikedEntry>   spiked    = new HashMap<>();
        final Map<UUID, PossessedEntry> possessed = new HashMap<>();
    }

    /** Shared base so onRemove can iterate all entry types generically. */
    private abstract static class ControlEntry {
        final UUID target;
        final ResourceKey<Level> worldKey;
        int ticksLeft;

        ControlEntry(UUID target, ResourceKey<Level> worldKey, int ticksLeft) {
            this.target    = target;
            this.worldKey  = worldKey;
            this.ticksLeft = ticksLeft;
        }
    }

    private static class CompelEntry extends ControlEntry {
        CompelEntry(UUID target, ResourceKey<Level> worldKey) {
            super(target, worldKey, COMPEL_DURATION);
        }
    }

    private static class SpikedEntry extends ControlEntry {
        SpikedEntry(UUID target, ResourceKey<Level> worldKey) {
            super(target, worldKey, SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION);
        }
    }

    private static class PossessedEntry extends ControlEntry {
        int attackCd; // replaces ATTACK_CD_TAG on the entity

        PossessedEntry(UUID target, ResourceKey<Level> worldKey) {
            super(target, worldKey, ULT_CONTROL_DURATION);
            this.attackCd = 0;
        }
    }

    private static PsychicState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new PsychicState());
    }

    /* ============================================================
       PARTICLE CONSTANTS (CACHED)
       ============================================================ */

    // Leech (passive)
    private static final DustParticleOptions LEECH_DUST_MAIN  = new DustParticleOptions(new Vector3f(0.80f, 0.00f, 0.90f), 0.8f);
    private static final DustParticleOptions LEECH_DUST_LIGHT = new DustParticleOptions(new Vector3f(1.00f, 0.40f, 0.90f), 0.5f);
    private static final DustParticleOptions LEECH_DUST_END   = new DustParticleOptions(new Vector3f(1.00f, 0.20f, 0.80f), 1.0f);

    // Compel (primary)
    private static final DustParticleOptions COMPEL_DUST_MAIN  = new DustParticleOptions(new Vector3f(0.90f, 0.20f, 0.60f), 0.7f);
    private static final DustParticleOptions COMPEL_DUST_LIGHT = new DustParticleOptions(new Vector3f(1.00f, 0.60f, 0.90f), 0.5f);

    // Spike (secondary) — impact, aura, beam
    private static final DustParticleOptions SPIKE_DUST_IMPACT_DARK  = new DustParticleOptions(new Vector3f(0.50f, 0.00f, 0.60f), 1.0f);
    private static final DustParticleOptions SPIKE_DUST_IMPACT_LIGHT = new DustParticleOptions(new Vector3f(0.90f, 0.20f, 0.60f), 0.8f);
    private static final DustParticleOptions SPIKE_DUST_AURA_MAIN    = new DustParticleOptions(new Vector3f(0.90f, 0.20f, 0.60f), 0.6f);
    private static final DustParticleOptions SPIKE_DUST_AURA_LIGHT   = new DustParticleOptions(new Vector3f(0.50f, 0.00f, 0.60f), 0.5f);
    private static final DustParticleOptions SPIKE_BEAM_MAIN         = new DustParticleOptions(new Vector3f(0.90f, 0.20f, 0.60f), 0.6f);
    private static final DustParticleOptions SPIKE_BEAM_LIGHT        = new DustParticleOptions(new Vector3f(1.00f, 0.60f, 0.90f), 0.4f);

    // Ultimate/Puppetry
    private static final DustParticleOptions ULT_DUST_MAIN  = new DustParticleOptions(new Vector3f(1.00f, 0.20f, 0.80f), 0.9f);
    private static final DustParticleOptions ULT_DUST_LIGHT = new DustParticleOptions(new Vector3f(1.00f, 0.60f, 0.90f), 0.6f);
    private static final DustParticleOptions ULT_DUST_DARK  = new DustParticleOptions(new Vector3f(0.70f, 0.00f, 1.00f), 1.1f);

    /* ============================================================
       TUNING CONSTANTS
       ============================================================ */

    // Passive
    private static final float LEECH_HEAL       = 3.5f;
    private static final long  LEECH_CDR_MS     = 500;
    private static final int   LEECH_INTERNAL_CD = 35;

    // Compel (primary)
    private static final int    COMPEL_DURATION         = 80;
    private static final double COMPEL_STOP_DISTANCE    = 2.3;
    private static final double COMPEL_MOB_SPEED        = 0.20;
    private static final double COMPEL_PLAYER_ACCEL     = 0.14;
    private static final double COMPEL_PLAYER_MAX_SPEED = 0.40;
    private static final float  COMPEL_LOOK_STRENGTH    = 0.25f;
    private static final double COMPEL_LOOK_MIN_DIST    = 2.5;
    private static final double COMPEL_PROJECTILE_SPEED = 1.3;

    // Spike (secondary)
    private static final double SPIKE_BEAM_RANGE        = 18.0;
    private static final double SPIKE_CHAIN_RADIUS      = 2.0;
    private static final int    SPIKE_CHAIN_COUNT       = 8;
    private static final int    SPIKE_STUN_DURATION     = 30;
    private static final int    SPIKE_SLOW_DURATION     = 100;
    private static final int    SPIKE_STUN_AMPLIFIER    = 4;
    private static final int    SPIKE_SLOW_AMPLIFIER    = 1;
    private static final int    SPIKE_FATIGUE_DURATION  = 120;
    private static final int    SPIKE_FATIGUE_AMPLIFIER = 0;
    private static final int    SPIKE_JAGGED_SEGMENTS   = 18;
    private static final int    SPIKE_CHAIN_SEGMENTS    = 10;
    private static final double SPIKE_JAGGED_OFFSET     = 0.55;

    // Ultimate
    private static final double ULT_RANGE            = 40.0;
    private static final int    ULT_CONTROL_DURATION  = 180;
    private static final double ULT_PLAYER_ACCEL      = 0.30;
    private static final double ULT_PLAYER_MAX_SPEED  = 0.65;
    private static final double ULT_MOB_SPEED         = 0.36;
    private static final float  ULT_LOOK_STRENGTH     = 0.3f;
    private static final double ULT_STOP_DISTANCE     = 1.5;
    private static final int    ATTACK_COOLDOWN        = 35;

    // Easter egg
    private static final double COMPEL_CHAT_CHANCE = 0.05;
    private static final List<String> STUPID_MESSAGES = List.of(
            "I think I'll use my credit card.",
            "erm is this thing on?",
            "do u guys like Radiohead?",
            "I LISTEN TO ALEXG I LISTEN TO ALEXG I LISTEN TO ALEXG I LISTEN TO ALEXG.",
            "roflcopter!!!",
            "i really need a wee",
            "hop on MARVEL RIVALS?",
            "my tummy hurt :(",
            "hello everyone my name is welcome",
            "morp",
            "haha six seven",
            "throw me into the wolves, and i'll come back pregnant",
            "JOIN THE REBELLION"
    );

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        // Clear any stale state from a previous power assignment
        ACTIVE_STATES.remove(player.getUUID());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        PsychicState state = ACTIVE_STATES.remove(player.getUUID());

        // Projectile entities have no state entry — world scan still required here,
        // but this only runs on disconnect/death so the cost is acceptable.
        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                w.getEntitiesOfClass(CompelEntity.class,   player.getBoundingBox().inflate(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);
                w.getEntitiesOfClass(PuppetryEntity.class, player.getBoundingBox().inflate(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);
            }
        }

        if (state == null || player.getServer() == null) return;

        // OPTIMIZATION: Release controlled entities via exact UUID lookup instead of
        // the old broad entity scan across ALL entities in ALL worlds (150-block radius).
        MinecraftServer server = player.getServer();
        cleanupEntries(server, state.compelled.values(),  le -> le.removeEffect(ModEffects.COMPELLED));
        cleanupEntries(server, state.spiked.values(),     le -> {
            le.removeEffect(ModEffects.STUN);
            le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            le.removeEffect(MobEffects.DIG_SLOWDOWN);
        });
        cleanupEntries(server, state.possessed.values(),  le -> {
            le.removeEffect(ModEffects.POSSESSED);
            le.removeEffect(MobEffects.DIG_SLOWDOWN);
        });
    }

    /** Resolve each entry's UUID in its stored world and run a cleanup action. */
    private static void cleanupEntries(MinecraftServer server,
                                       Collection<? extends ControlEntry> entries,
                                       Consumer<LivingEntity> fn) {
        for (ControlEntry entry : entries) {
            ServerLevel w = server.getLevel(entry.worldKey);
            if (w == null) continue;
            Entity ent = w.getEntity(entry.target);
            if (ent instanceof LivingEntity le) fn.accept(le);
        }
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        PsychicState state = getState(player);

        // Tick the leech internal cooldown (replaces tickTag on player)
        if (state.leechCd > 0) state.leechCd--;

        tickCompel(player, state);
        tickSpike(player, state);
        tickUltimate(player, state);
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;

        PsychicState state = getState(attacker);
        if (state.leechCd > 0) return;

        // OPTIMIZATION: Direct map lookup instead of scanning command tags on the entity
        UUID tid = target.getUUID();
        if (!state.compelled.containsKey(tid)
                && !state.spiked.containsKey(tid)
                && !state.possessed.containsKey(tid)) return;

        ServerLevel world = attacker.serverLevel();

        attacker.heal(LEECH_HEAL);
        spawnLeechParticles(world, attacker, target);
        world.playSound(null, attacker.blockPosition(),
                SoundEvents.WARDEN_HEARTBEAT,
                attacker.getSoundSource(), 0.6f, 1.4f);
        PowerManager.reduceAllCooldowns(attacker, LEECH_CDR_MS);

        state.leechCd = LEECH_INTERNAL_CD;
    }

    /* ============================================================
       PASSIVE — MIND SAP
       ============================================================ */

    private static void spawnLeechParticles(ServerLevel world, LivingEntity attacker, LivingEntity target) {
        Vec3 from    = new Vec3(target.getX(),   target.getY(0.7),   target.getZ());
        Vec3 to      = new Vec3(attacker.getX(), attacker.getY(0.7), attacker.getZ());
        Vec3 step    = to.subtract(from).scale(1.0 / 10);
        Vec3 current = from;

        for (int i = 0; i < 10; i++) {
            double curve = Math.sin(i / 10.0 * Math.PI) * 0.15;
            world.sendParticles(LEECH_DUST_MAIN,  current.x, current.y - curve, current.z, 2, 0.05, 0.05, 0.05, 0.0);
            world.sendParticles(LEECH_DUST_LIGHT, current.x, current.y - curve, current.z, 1, 0.02, 0.02, 0.02, 0.0);
            current = current.add(step);
        }

        world.sendParticles(LEECH_DUST_END,
                attacker.getX(), attacker.getY(0.6), attacker.getZ(),
                10, 0.3, 0.4, 0.3, 0.02);
    }

    /* ============================================================
       PRIMARY — COMPEL
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        CompelEntity proj = new CompelEntity(ModEntities.COMPEL_ENTITY.get(), world);
        proj.setOwner(player);

        Vec3 eyePos = player.getEyePosition();
        proj.setPos(eyePos.x, eyePos.y, eyePos.z);

        Vec3 look = player.getViewVector(1.0f);
        proj.setDeltaMovement(
                look.x * COMPEL_PROJECTILE_SPEED,
                look.y * COMPEL_PROJECTILE_SPEED,
                look.z * COMPEL_PROJECTILE_SPEED
        );

        world.addFreshEntity(proj);
        world.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                player.getSoundSource(), 0.7f, 1.2f);
    }

    public static void applyCompel(ServerPlayer caster, LivingEntity target) {
        PsychicState state = getState(caster);
        // Overwrite any existing entry to reset the timer
        state.compelled.put(target.getUUID(),
                new CompelEntry(target.getUUID(), target.level().dimension()));

        target.addEffect(new MobEffectInstance(
                ModEffects.COMPELLED, COMPEL_DURATION, 0, false, false, true));

        // Easter egg: compelled player says something stupid in chat
        if (target instanceof ServerPlayer player && player.getServer() != null) {
            if (player.getRandom().nextDouble() < COMPEL_CHAT_CHANCE) {
                String msg = STUPID_MESSAGES.get(player.getRandom().nextInt(STUPID_MESSAGES.size()));
                Component chatText = Component.literal(
                        "<" + player.getName().getString() + "> " + msg);
                player.getServer().getPlayerList().broadcastSystemMessage(chatText, false);
            }
        }
    }

    private static void tickCompel(ServerPlayer player, PsychicState state) {
        // DEBUG: test control by compelling the caster itself towards the nearest entity
        if (player.getTags().contains("psy_debug")) {
            ServerLevel w = player.serverLevel();
            LivingEntity nearest = w.getNearestEntity(
                    LivingEntity.class,
                    TargetingConditions.DEFAULT,
                    player, player.getX(), player.getY(), player.getZ(),
                    player.getBoundingBox().inflate(20)
            );
            if (nearest != null && nearest != player) {
                Vec3 dir = nearest.getEyePosition().subtract(player.getEyePosition());
                double dist = dir.length();
                if (dist > 0.0001) dir = dir.normalize();
                if (dist > COMPEL_LOOK_MIN_DIST) forceLook(player, dir, COMPEL_LOOK_STRENGTH);
                applyMovement(player, dir, dist, COMPEL_MOB_SPEED, COMPEL_PLAYER_ACCEL, COMPEL_PLAYER_MAX_SPEED, COMPEL_STOP_DISTANCE);
                applyControlEffects(player);
                spawnCompelParticles(w, player);
            }
        }

        if (state.compelled.isEmpty()) return;

        Iterator<CompelEntry> it = state.compelled.values().iterator();
        while (it.hasNext()) {
            CompelEntry entry = it.next();
            ServerLevel w = Objects.requireNonNull(player.getServer()).getLevel(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) {
                le.removeEffect(ModEffects.COMPELLED);
                it.remove();
                continue;
            }

            Vec3 dir = player.getEyePosition().subtract(le.getEyePosition());
            double distance = dir.length();
            if (distance > 0.0001) dir = dir.normalize();

            if (distance > COMPEL_LOOK_MIN_DIST) {
                forceLook(le, dir, COMPEL_LOOK_STRENGTH);
            }
            applyMovement(le, dir, distance, COMPEL_MOB_SPEED, COMPEL_PLAYER_ACCEL, COMPEL_PLAYER_MAX_SPEED, COMPEL_STOP_DISTANCE);
            applyControlEffects(le);
            spawnCompelParticles(w, le);
        }
    }

    private static void applyControlEffects(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 5, 1, true, false, false));
    }

    private static void spawnCompelParticles(ServerLevel world, LivingEntity entity) {
        world.sendParticles(COMPEL_DUST_MAIN,
                entity.getX(), entity.getY(0.6), entity.getZ(),
                2, 0.2, 0.3, 0.2, 0.01);
        world.sendParticles(COMPEL_DUST_LIGHT,
                entity.getX(), entity.getY(0.6), entity.getZ(),
                1, 0.3, 0.4, 0.3, 0.005);
    }

    /* ============================================================
       SECONDARY — SPIKE
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 end    = origin.add(look.scale(SPIKE_BEAM_RANGE));

        // Terminate beam at first block hit
        var blockHit = world.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        spawnJaggedBeam(world, origin, end, SPIKE_JAGGED_SEGMENTS, SPIKE_JAGGED_OFFSET);

        LivingEntity primaryTarget = getEntityOnBeam(world, player, origin, end);

        // Collect chain targets near the beam line
        Vec3 finalEnd = end;
        List<LivingEntity> chainTargets = new ArrayList<>();
        world.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin, finalEnd).inflate(SPIKE_CHAIN_RADIUS),
                        e -> e.isAlive() && e != player && e != primaryTarget
                ).stream()
                .filter(e -> distanceToLine(origin, finalEnd, e.getEyePosition()) < SPIKE_CHAIN_RADIUS)
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .limit(SPIKE_CHAIN_COUNT)
                .forEach(chainTargets::add);

        PsychicState state = getState(player);

        if (primaryTarget != null) {
            applySpike(state, primaryTarget);
            spawnSpikeImpactParticles(world, primaryTarget);
        }

        Vec3 chainOrigin = (primaryTarget != null) ? primaryTarget.getEyePosition() : end;
        for (LivingEntity chain : chainTargets) {
            spawnJaggedBeam(world, chainOrigin, chain.getEyePosition(), SPIKE_CHAIN_SEGMENTS, SPIKE_JAGGED_OFFSET * 0.7);
            applySpike(state, chain);
            spawnSpikeImpactParticles(world, chain);
            chainOrigin = chain.getEyePosition();
        }

        world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, player.getSoundSource(), 0.8f, 0.7f);
        world.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,  player.getSoundSource(), 0.3f, 1.8f);
    }

    private static void applySpike(PsychicState state, LivingEntity target) {
        // Overwrite entry to reset timer if hit again
        state.spiked.put(target.getUUID(),
                new SpikedEntry(target.getUUID(), target.level().dimension()));

        target.addEffect(new MobEffectInstance(
                ModEffects.STUN, SPIKE_STUN_DURATION, SPIKE_STUN_AMPLIFIER, false, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION, SPIKE_SLOW_AMPLIFIER, false, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, SPIKE_FATIGUE_DURATION, SPIKE_FATIGUE_AMPLIFIER, false, false, true));
    }

    /**
     * OPTIMIZATION: Replaces a broad 23-block getEntitiesByClass scan (every tick)
     * with direct iteration over only the entities we know are spiked.
     */
    private static void tickSpike(ServerPlayer player, PsychicState state) {
        if (state.spiked.isEmpty()) return;

        long time = player.serverLevel().getGameTime();

        Iterator<SpikedEntry> it = state.spiked.values().iterator();
        while (it.hasNext()) {
            SpikedEntry entry = it.next();
            ServerLevel w = Objects.requireNonNull(player.getServer()).getLevel(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) { it.remove(); continue; }

            spawnSpikeAuraParticles(w, le);

            // Send a 15-tick subtle shake every 10 ticks to avoid network spam
            if (le instanceof ServerPlayer targetPlayer && time % 10 == 0) {
                shake(targetPlayer, 15, 0.05f);
            }
        }
    }

    public static void shake(ServerPlayer target, int ticks, float strength) {
        CameraShake.shakeNearby(target, 10.0, ticks, strength);
    }

    private static void spawnSpikeImpactParticles(ServerLevel world, LivingEntity entity) {
        world.sendParticles(SPIKE_DUST_IMPACT_DARK,  entity.getX(), entity.getY(0.5), entity.getZ(), 20, 0.4, 0.5, 0.4, 0.03);
        world.sendParticles(SPIKE_DUST_IMPACT_LIGHT, entity.getX(), entity.getY(0.5), entity.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
    }

    private static void spawnSpikeAuraParticles(ServerLevel world, LivingEntity entity) {
        world.sendParticles(SPIKE_DUST_AURA_MAIN,  entity.getX(), entity.getY(0.7), entity.getZ(), 2, 0.25, 0.30, 0.25, 0.01);
        world.sendParticles(SPIKE_DUST_AURA_LIGHT, entity.getX(), entity.getY(0.4), entity.getZ(), 1, 0.20, 0.20, 0.20, 0.005);
    }

    /* ============================================================
       ULTIMATE — PUPPETRY
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        PuppetryEntity proj = new PuppetryEntity(ModEntities.PUPPETRY_ENTITY.get(), world);
        proj.setOwner(player);

        Vec3 eyePos = player.getEyePosition();
        proj.setPos(eyePos.x, eyePos.y, eyePos.z);

        Vec3 look = player.getViewVector(1.0f);
        proj.setDeltaMovement(
                look.x * PuppetryEntity.SPEED,
                look.y * PuppetryEntity.SPEED,
                look.z * PuppetryEntity.SPEED
        );

        world.addFreshEntity(proj);
        world.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                player.getSoundSource(), 1.0f, 0.6f);
    }

    /**
     * Called by PuppetryEntity when it hits a target.
     * NOTE: PuppetryEntity must be updated to pass the caster as the first argument:
     * PsychicPower.applyUltimateControl((ServerPlayer) getOwner(), target)
     */
    public static void applyUltimateControl(ServerPlayer caster, LivingEntity target) {
        PsychicState state = getState(caster);
        // Overwrite to reset timer if controlled again
        state.possessed.put(target.getUUID(),
                new PossessedEntry(target.getUUID(), target.level().dimension()));

        target.addEffect(new MobEffectInstance(
                ModEffects.POSSESSED, ULT_CONTROL_DURATION, 0, false, false, true));

        if (target.level() instanceof ServerLevel world) {
            world.playSound(null, target.blockPosition(),
                    ModSounds.POSSESSION.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /**
     * OPTIMIZATION: Replaces a broad 40-block getEntitiesByClass scan filtered by tag
     * (every tick) with direct iteration over only the entities we know are possessed.
     */
    private static void tickUltimate(ServerPlayer player, PsychicState state) {
        if (state.possessed.isEmpty()) return;

        ServerLevel playerWorld = player.serverLevel();

        Iterator<PossessedEntry> it = state.possessed.values().iterator();
        while (it.hasNext()) {
            PossessedEntry entry = it.next();
            ServerLevel w = Objects.requireNonNull(player.getServer()).getLevel(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) {
                le.removeEffect(ModEffects.POSSESSED);
                it.remove();
                continue;
            }

            Vec3 cursorPos = getCursorTarget(player, playerWorld);
            Vec3 dir = cursorPos.subtract(le.getEyePosition());
            double dist = dir.length();
            if (dist > 0.0001) dir = dir.normalize();

            forceLook(le, dir, ULT_LOOK_STRENGTH);
            applyMovement(le, dir, dist, ULT_MOB_SPEED, ULT_PLAYER_ACCEL, ULT_PLAYER_MAX_SPEED, ULT_STOP_DISTANCE);

            le.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, 5, 2, true, false, false));

            spawnControlParticles(w, le);
            handleControlledAttacks(w, le, entry);
        }
    }

    private static Vec3 getCursorTarget(ServerPlayer player, ServerLevel world) {
        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 end    = origin.add(look.scale(ULT_RANGE));

        var hit = world.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return (hit.getType() != HitResult.Type.MISS) ? hit.getLocation() : end;
    }

    private static void handleControlledAttacks(ServerLevel world, LivingEntity entity, PossessedEntry entry) {
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) == null) return;

        // OPTIMIZATION: attack cooldown stored in PossessedEntry, replacing ATTACK_CD_TAG on entity
        if (entry.attackCd > 0) {
            entry.attackCd--;
            // Still face nearest target even while on cooldown
            LivingEntity target = findNearestTarget(world, entity);
            if (target != null && entity.hasLineOfSight(target)) {
                forceLook(entity, target.getEyePosition().subtract(entity.getEyePosition()).normalize(), ULT_LOOK_STRENGTH);
            }
            return;
        }

        LivingEntity target = findNearestTarget(world, entity);
        if (target == null || !entity.hasLineOfSight(target)) return;

        forceLook(entity, target.getEyePosition().subtract(entity.getEyePosition()).normalize(), ULT_LOOK_STRENGTH);

        if (entity instanceof Mob mob) {
            if (mob.doHurtTarget(target)) entry.attackCd = ATTACK_COOLDOWN;
        } else if (entity instanceof ServerPlayer controlledPlayer) {
            controlledPlayer.swing(InteractionHand.MAIN_HAND, true);
            controlledPlayer.attack(target);
            entry.attackCd = ATTACK_COOLDOWN;
        }
    }

    private static LivingEntity findNearestTarget(ServerLevel world, LivingEntity attacker) {
        LivingEntity closest      = null;
        double       closestDistSq = 3.0 * 3.0;

        for (LivingEntity e : world.getEntitiesOfClass(
                LivingEntity.class,
                attacker.getBoundingBox().inflate(3.0),
                en -> en.isAlive() && en != attacker && attacker.hasLineOfSight(en))) {

            double dist = attacker.distanceToSqr(e);
            if (dist < closestDistSq) {
                closestDistSq = dist;
                closest = e;
            }
        }
        return closest;
    }

    private static void spawnControlParticles(ServerLevel world, LivingEntity entity) {
        world.sendParticles(ULT_DUST_MAIN,  entity.getX(), entity.getY(0.7), entity.getZ(), 4, 0.35, 0.45, 0.35, 0.02);
        world.sendParticles(ULT_DUST_LIGHT, entity.getX(), entity.getY(0.5), entity.getZ(), 2, 0.30, 0.30, 0.30, 0.01);
        world.sendParticles(ULT_DUST_DARK,  entity.getX(), entity.getY(0.6), entity.getZ(), 3, 0.30, 0.40, 0.30, 0.02);
    }

    /* ============================================================
       MOVEMENT AND LOOK  (NEOFORGE LOGIC)
       ============================================================ */

    private static void applyMovement(LivingEntity entity, Vec3 dir, double distance,
                                      double mobSpeed, double playerAccel, double playerMaxSpeed,
                                      double stopDistance) {
        Vec3 velocity = entity.getDeltaMovement();

        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();

            if (distance > stopDistance) {
                Vec3 flatDir = new Vec3(dir.x, 0, dir.z);
                if (flatDir.lengthSqr() > 0.0001) flatDir = flatDir.normalize();
                entity.setDeltaMovement(flatDir.x * mobSpeed, velocity.y, flatDir.z * mobSpeed);
                entity.hasImpulse = true;

                if (entity.level() instanceof ServerLevel sw) {
                    sw.getChunkSource().broadcastAndSend(entity, new ClientboundTeleportEntityPacket(entity));
                }
            } else {
                entity.setDeltaMovement(velocity.x * 0.4, velocity.y, velocity.z * 0.4);
                entity.hasImpulse = true;
            }

        } else if (entity instanceof ServerPlayer player) {
            if (distance > stopDistance) {
                Vec3 flatDir = new Vec3(dir.x, 0, dir.z);
                if (flatDir.lengthSqr() > 0.0001) flatDir = flatDir.normalize();

                Vec3 newVel = velocity.add(flatDir.scale(playerAccel));
                if (newVel.horizontalDistance() > playerMaxSpeed) {
                    newVel = new Vec3(newVel.x, 0, newVel.z)
                            .normalize().scale(playerMaxSpeed)
                            .add(0, velocity.y, 0);
                }
                entity.setDeltaMovement(newVel.x, velocity.y, newVel.z);
                entity.hasImpulse = true;
            } else {
                entity.setDeltaMovement(velocity.x * 0.4, velocity.y, velocity.z * 0.4);
                entity.hasImpulse = true;
            }
        }
    }

    private static void forceLook(LivingEntity entity, Vec3 dir, float strength) {
        if (dir.lengthSqr() < 0.0001) return;

        float  targetYaw   = (float)(Math.toDegrees(Mth.atan2(dir.z, dir.x))) - 90f;
        double horizontal  = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float  targetPitch = (float)(-Math.toDegrees(Mth.atan2(dir.y, horizontal)));

        if (entity instanceof ServerPlayer sp) {
            float yawDiff   = Mth.wrapDegrees(targetYaw - sp.getYRot());
            float pitchDiff = targetPitch - sp.getXRot();

            sp.connection.send(new ClientboundPlayerPositionPacket(
                    0, 0, 0,
                    yawDiff   * strength,
                    pitchDiff * strength,
                    java.util.EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z,
                            RelativeMovement.Y_ROT, RelativeMovement.X_ROT),
                    0
            ));
        } else {
            entity.setYRot(targetYaw);
            entity.setYHeadRot(targetYaw);
            entity.setXRot(targetPitch);
            entity.setYBodyRot(targetYaw);
        }
    }

    /* ============================================================
       BEAM HELPERS
       ============================================================ */

    private static void spawnJaggedBeam(ServerLevel world, Vec3 from, Vec3 to, int segments, double offset) {
        Vec3 step    = to.subtract(from).scale(1.0 / segments);
        Vec3 beamDir = to.subtract(from).normalize();
        Vec3 perp    = Math.abs(beamDir.y) < 0.9
                ? new Vec3(-beamDir.z, 0, beamDir.x).normalize()
                : new Vec3(1, 0, 0);
        Vec3 perp2 = beamDir.cross(perp).normalize();

        Vec3 current = from;
        Random rng = new Random(from.hashCode()); // stable seed = consistent look per cast

        for (int i = 0; i < segments; i++) {
            Vec3 next = current.add(step);

            double jag  = (i % 2 == 0 ? 1 : -1) * offset * (0.5 + rng.nextDouble() * 0.5);
            double jag2 = (i % 3 == 0 ? 1 : -1) * offset * 0.4 * rng.nextDouble();
            Vec3 jaggedNext = next.add(perp.scale(jag)).add(perp2.scale(jag2));

            for (int j = 0; j <= 4; j++) {
                Vec3 pos = current.lerp(jaggedNext, j / 4.0);
                world.sendParticles(SPIKE_BEAM_MAIN,  pos.x, pos.y, pos.z, 1, 0.00,  0.00,  0.00,  0);
                world.sendParticles(SPIKE_BEAM_LIGHT, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0);
            }

            current = jaggedNext;
        }
    }

    private static LivingEntity getEntityOnBeam(ServerLevel world, ServerPlayer player,
                                                Vec3 origin, Vec3 end) {
        LivingEntity closest      = null;
        double       closestDistSq = Double.MAX_VALUE;

        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, end).inflate(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().inflate(0.3).clip(origin, end);
            if (hit.isPresent()) {
                double dist = origin.distanceToSqr(hit.get());
                if (dist < closestDistSq) {
                    closestDistSq = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private static double distanceToLine(Vec3 lineStart, Vec3 lineEnd, Vec3 point) {
        Vec3  line = lineEnd.subtract(lineStart);
        double len  = line.length();
        if (len < 0.0001) return point.distanceTo(lineStart);
        double t = Mth.clamp(
                point.subtract(lineStart).dot(line) / (len * len), 0, 1);
        return point.distanceTo(lineStart.add(line.scale(t)));
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.psychic.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.psychic.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.psychic.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.psychic.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 14_000; }
    @Override public long getSecondaryCooldownMs() { return 29_000; }
    @Override public long getUltimateCooldownMs()  { return 560_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.psychic.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.psychic.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.psychic.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.psychic.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.psychic.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.psychic.description.ultimate").getString();
    }
}