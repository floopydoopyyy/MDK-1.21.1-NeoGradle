package com.loopy.loopypowers.power;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.CompelEntity;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.entity.PuppetryEntity;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.*;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Consumer;

public class PsychicPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, PsychicState> ACTIVE_STATES = new HashMap<>();

    private static class PsychicState {
        int leechCd = 0;
        final Map<UUID, CompelEntry>   compelled = new HashMap<>();
        final Map<UUID, SpikedEntry>   spiked    = new HashMap<>();
        final Map<UUID, PossessedEntry> possessed = new HashMap<>();
    }

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
        int attackCd;

        PossessedEntry(UUID target, ResourceKey<Level> worldKey) {
            super(target, worldKey, ULT_CONTROL_DURATION);
            this.attackCd = 0;
        }
    }

    private static PsychicState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new PsychicState());
    }

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
    private static final double COMPEL_MOB_SPEED        = 0.18;
    private static final double COMPEL_PLAYER_ACCEL     = 0.14;
    private static final double COMPEL_PLAYER_MAX_SPEED = 0.40;
    private static final float  COMPEL_LOOK_STRENGTH    = 0.25f;
    private static final double COMPEL_LOOK_MIN_DIST    = 2.5;
    private static final double COMPEL_PROJECTILE_SPEED = 1.3;
    private static final float  COMPEL_BREAK_CHANCE     = 0.4f;

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

    // Ultimate
    private static final double ULT_RANGE            = 40.0;
    private static final int    ULT_CONTROL_DURATION  = 180;
    private static final double ULT_PLAYER_ACCEL      = 0.30;
    private static final double ULT_PLAYER_MAX_SPEED  = 0.65;
    private static final double ULT_MOB_SPEED         = 0.33;
    private static final float  ULT_LOOK_STRENGTH     = 0.3f;
    private static final double ULT_STOP_DISTANCE     = 1.5;
    private static final int    ATTACK_COOLDOWN        = 35;
    private static final float  POSSESS_BREAK_CHANCE   = 0.3f;

    // Break Rewards
    private static final float  BREAK_HEAL_REWARD       = 10.0f;
    private static final long   BREAK_CDR_REWARD_MS     = 2500;

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
        ACTIVE_STATES.remove(player.getUUID());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        PsychicState state = ACTIVE_STATES.remove(player.getUUID());

        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                w.getEntitiesOfClass(CompelEntity.class,   player.getBoundingBox().inflate(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);
                w.getEntitiesOfClass(PuppetryEntity.class, player.getBoundingBox().inflate(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);
            }
        }

        if (state == null || player.getServer() == null) return;

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

    private static void cleanupEntries(MinecraftServer server, Collection<? extends ControlEntry> entries, Consumer<LivingEntity> fn) {
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

        if (state.leechCd > 0) state.leechCd--;

        tickCompel(player, state);
        tickSpike(player, state);
        tickUltimate(player, state);
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;

        PsychicState state = getState(attacker);
        UUID tid = target.getUUID();
        ServerLevel world = attacker.serverLevel();

        boolean isCompelled = state.compelled.containsKey(tid);
        boolean isPossessed = state.possessed.containsKey(tid);
        boolean isSpiked    = state.spiked.containsKey(tid);

        if (!isCompelled && !isPossessed && !isSpiked) return;
        if (state.leechCd > 0) return;

        attacker.heal(LEECH_HEAL);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new PsychicLeechPayload(target.getId(), attacker.getId()));

        world.playSound(null, attacker.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, attacker.getSoundSource(), 0.6f, 1.4f);
        PowerManager.reduceAllCooldowns(attacker, LEECH_CDR_MS);

        state.leechCd = LEECH_INTERNAL_CD;
    }

    public static boolean onDamageGlobal(LivingEntity victim, DamageSource source) {
        Entity attackerEnt = source.getEntity();
        if (attackerEnt == null) return false;

        if (victim instanceof ServerPlayer player) {
            PsychicState state = ACTIVE_STATES.get(player.getUUID());
            if (state != null) {
                UUID attackerId = attackerEnt.getUUID();
                if (state.compelled.containsKey(attackerId) || state.possessed.containsKey(attackerId)) {
                    return true;
                }
            }
        }

        if (attackerEnt instanceof ServerPlayer player) {
            PsychicState state = ACTIVE_STATES.get(player.getUUID());
            if (state != null) {
                UUID victimId = victim.getUUID();
                boolean isCompelled = state.compelled.containsKey(victimId);
                boolean isPossessed = state.possessed.containsKey(victimId);
                boolean brokeControl = false;

                if (isCompelled && player.getRandom().nextFloat() < COMPEL_BREAK_CHANCE) {
                    state.compelled.remove(victimId);
                    victim.removeEffect(ModEffects.COMPELLED);
                    brokeControl = true;
                }

                if (isPossessed && player.getRandom().nextFloat() < POSSESS_BREAK_CHANCE) {
                    state.possessed.remove(victimId);
                    victim.removeEffect(ModEffects.POSSESSED);
                    victim.removeEffect(MobEffects.DIG_SLOWDOWN);
                    brokeControl = true;
                }

                if (brokeControl && player.level() instanceof ServerLevel w) {
                    w.playSound(null, victim.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.2f);
                    w.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, player.getSoundSource(), 1.0f, 1.8f);

                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new PsychicLeechPayload(victim.getId(), player.getId()));

                    player.heal(BREAK_HEAL_REWARD);
                    PowerManager.reduceAllCooldowns(player, BREAK_CDR_REWARD_MS);
                }
            }
        }

        return false;
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
        world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, player.getSoundSource(), 0.7f, 1.2f);
    }

    public static void applyCompel(ServerPlayer caster, LivingEntity target) {
        PsychicState state = getState(caster);
        state.compelled.put(target.getUUID(), new CompelEntry(target.getUUID(), target.level().dimension()));

        target.addEffect(new MobEffectInstance(ModEffects.COMPELLED, COMPEL_DURATION, 0, false, false, true));

        if (target instanceof ServerPlayer player && player.getServer() != null) {
            if (player.getRandom().nextDouble() < COMPEL_CHAT_CHANCE) {
                String msg = STUPID_MESSAGES.get(player.getRandom().nextInt(STUPID_MESSAGES.size()));
                Component chatText = Component.literal("<" + player.getName().getString() + "> " + msg);
                player.getServer().getPlayerList().broadcastSystemMessage(chatText, false);
            }
        }
    }

    private static void tickCompel(ServerPlayer player, PsychicState state) {
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

            // Flatten look angle when very close to prevent snapping down to the floor
            if (distance > COMPEL_LOOK_MIN_DIST) {
                forceLook(le, dir, COMPEL_LOOK_STRENGTH);
            } else {
                Vec3 flatDir = new Vec3(dir.x, 0, dir.z);
                if (flatDir.lengthSqr() > 0.001) {
                    forceLook(le, flatDir.normalize(), COMPEL_LOOK_STRENGTH);
                }
            }

            applyMovement(le, dir, distance, COMPEL_MOB_SPEED, COMPEL_PLAYER_ACCEL, COMPEL_PLAYER_MAX_SPEED, COMPEL_STOP_DISTANCE);

            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, true, false, false));

            // Dispatch Aura Payload
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(le, new CompelAuraPayload(le.getId()));
        }
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

        var blockHit = world.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(BlockPos.containing(origin)), new PsychicSpikeBeamPayload(origin, end, false));

        LivingEntity primaryTarget = getEntityOnBeam(world, player, origin, end);

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
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(primaryTarget, new PsychicSpikeImpactPayload(primaryTarget.getId()));
        }

        Vec3 chainOrigin = (primaryTarget != null) ? primaryTarget.getEyePosition() : end;
        for (LivingEntity chain : chainTargets) {
            PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(BlockPos.containing(chainOrigin)), new PsychicSpikeBeamPayload(chainOrigin, chain.getEyePosition(), true));
            applySpike(state, chain);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(chain, new PsychicSpikeImpactPayload(chain.getId()));
            chainOrigin = chain.getEyePosition();
        }

        world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, player.getSoundSource(), 0.8f, 0.7f);
        world.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,  player.getSoundSource(), 0.3f, 1.8f);
    }

    private static void applySpike(PsychicState state, LivingEntity target) {
        state.spiked.put(target.getUUID(), new SpikedEntry(target.getUUID(), target.level().dimension()));

        target.addEffect(new MobEffectInstance(ModEffects.STUN, SPIKE_STUN_DURATION, SPIKE_STUN_AMPLIFIER, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION, SPIKE_SLOW_AMPLIFIER, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, SPIKE_FATIGUE_DURATION, SPIKE_FATIGUE_AMPLIFIER, false, false, true));
    }

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

            if (le instanceof ServerPlayer targetPlayer && time % 10 == 0) {
                shake(targetPlayer, 15, 0.05f);
            }

            // Dispatch Aura Payload
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(le, new SpikeAuraPayload(le.getId()));
        }
    }

    public static void shake(ServerPlayer target, int ticks, float strength) {
        CameraShake.shakeNearby(target, 10.0, ticks, strength);
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
        world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, player.getSoundSource(), 1.0f, 0.6f);
    }

    public static void applyUltimateControl(ServerPlayer caster, LivingEntity target) {
        PsychicState state = getState(caster);
        state.possessed.put(target.getUUID(), new PossessedEntry(target.getUUID(), target.level().dimension()));

        target.addEffect(new MobEffectInstance(ModEffects.POSSESSED, ULT_CONTROL_DURATION, 0, false, false, true));

        if (target.level() instanceof ServerLevel world) {
            world.playSound(null, target.blockPosition(), ModSounds.POSSESSION.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

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

            // Flatten look angle when very close to prevent snapping down to the floor
            if (dist > ULT_STOP_DISTANCE) {
                forceLook(le, dir, ULT_LOOK_STRENGTH);
            } else {
                Vec3 flatDir = new Vec3(dir.x, 0, dir.z);
                if (flatDir.lengthSqr() > 0.001) {
                    forceLook(le, flatDir.normalize(), ULT_LOOK_STRENGTH);
                }
            }

            applyMovement(le, dir, dist, ULT_MOB_SPEED, ULT_PLAYER_ACCEL, ULT_PLAYER_MAX_SPEED, ULT_STOP_DISTANCE);

            le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 5, 2, true, false, false));

            handleControlledAttacks(w, le, entry, player);

            // Dispatch Aura Payload
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(le, new PossessedAuraPayload(le.getId()));
        }
    }

    private static Vec3 getCursorTarget(ServerPlayer player, ServerLevel world) {
        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 end    = origin.add(look.scale(ULT_RANGE));

        var hit = world.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return (hit.getType() != HitResult.Type.MISS) ? hit.getLocation() : end;
    }

    private static void handleControlledAttacks(ServerLevel world, LivingEntity entity, PossessedEntry entry, ServerPlayer owner) {
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) == null) return;

        LivingEntity target = findNearestTarget(world, entity, owner);

        if (entry.attackCd > 0) {
            entry.attackCd--;
            if (target != null && entity.hasLineOfSight(target)) {
                forceLook(entity, target.getEyePosition().subtract(entity.getEyePosition()).normalize(), ULT_LOOK_STRENGTH);
            }
            return;
        }

        // If no target is found, clear vanilla AI target so they don't attack random entities/themselves
        if (target == null || !entity.hasLineOfSight(target)) {
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
            }
            return;
        }

        forceLook(entity, target.getEyePosition().subtract(entity.getEyePosition()).normalize(), ULT_LOOK_STRENGTH);

        if (entity instanceof Mob mob) {
            mob.setTarget(target);
            if (mob.doHurtTarget(target)) {
                entry.attackCd = ATTACK_COOLDOWN;
            }
        } else if (entity instanceof ServerPlayer controlledPlayer) {
            controlledPlayer.swing(InteractionHand.MAIN_HAND, true);
            controlledPlayer.attack(target);
            entry.attackCd = ATTACK_COOLDOWN;
        }
    }

    private static LivingEntity findNearestTarget(ServerLevel world, LivingEntity attacker, ServerPlayer owner) {
        LivingEntity closest      = null;
        double       closestDistSq = 3.0 * 3.0;

        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(3.0),
                en -> en.isAlive() && en.getId() != attacker.getId() && en.getId() != owner.getId() && attacker.hasLineOfSight(en))) {

            double dist = attacker.distanceToSqr(e);
            if (dist < closestDistSq) {
                closestDistSq = dist;
                closest = e;
            }
        }
        return closest;
    }

    /* ============================================================
       MOVEMENT AND LOOK
       ============================================================ */

    private static void applyMovement(LivingEntity entity, Vec3 dir, double distance,
                                      double mobSpeed, double playerAccel, double playerMaxSpeed,
                                      double stopDistance) {
        Vec3 velocity = entity.getDeltaMovement();

        double nextY = velocity.y;
        if (entity.horizontalCollision && entity.onGround()) {
            nextY = 0.5;
        }

        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();

            if (distance > stopDistance) {
                Vec3 flatDir = new Vec3(dir.x, 0, dir.z);
                if (flatDir.lengthSqr() > 0.0001) flatDir = flatDir.normalize();
                entity.setDeltaMovement(flatDir.x * mobSpeed, nextY, flatDir.z * mobSpeed);
                entity.hasImpulse = true;

                if (entity.level() instanceof ServerLevel sw) {
                    sw.getChunkSource().broadcastAndSend(entity, new ClientboundTeleportEntityPacket(entity));
                }
            } else {
                entity.setDeltaMovement(velocity.x * 0.4, nextY, velocity.z * 0.4);
                entity.hasImpulse = true;
            }

        } else if (entity instanceof ServerPlayer player) {
            if (distance > stopDistance) {
                Vec3 flatDir = new Vec3(dir.x, 0, dir.z);
                if (flatDir.lengthSqr() > 0.0001) flatDir = flatDir.normalize();

                Vec3 newVel = velocity.add(flatDir.scale(playerAccel));
                if (newVel.horizontalDistance() > playerMaxSpeed) {
                    newVel = new Vec3(newVel.x, 0, newVel.z).normalize().scale(playerMaxSpeed).add(0, nextY, 0);
                }
                entity.setDeltaMovement(newVel.x, nextY, newVel.z);
                entity.hasImpulse = true;
            } else {
                entity.setDeltaMovement(velocity.x * 0.4, nextY, velocity.z * 0.4);
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
                    0, 0, 0, yawDiff * strength, pitchDiff * strength,
                    java.util.EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z, RelativeMovement.Y_ROT, RelativeMovement.X_ROT), 0
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
        double t = Mth.clamp(point.subtract(lineStart).dot(line) / (len * len), 0, 1);
        return point.distanceTo(lineStart.add(line.scale(t)));
    }

    public static boolean cleansePsychic(LivingEntity target) {
        boolean cleansed = false;
        UUID targetId = target.getUUID();

        for (PsychicState state : ACTIVE_STATES.values()) {
            if (state.compelled.remove(targetId) != null) {
                target.removeEffect(ModEffects.COMPELLED);
                cleansed = true;
            }
            if (state.spiked.remove(targetId) != null) {
                target.removeEffect(ModEffects.STUN);
                target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                target.removeEffect(MobEffects.DIG_SLOWDOWN);
                cleansed = true;
            }
            if (state.possessed.remove(targetId) != null) {
                target.removeEffect(ModEffects.POSSESSED);
                target.removeEffect(MobEffects.DIG_SLOWDOWN);
                cleansed = true;
            }
        }

        return cleansed;
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