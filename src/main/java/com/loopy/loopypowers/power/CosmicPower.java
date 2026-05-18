package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.BlackHoleEntity;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.FateAuraPayload;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CosmicPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE
       ============================================================ */
    // Map structure: Attacker UUID -> (Target UUID -> FateInstance)
    private static final Map<UUID, Map<UUID, FateInstance>> ACTIVE_FATES = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_STARS = new HashMap<>();

    private static class FateInstance {
        public UUID ownerUuid;
        public float storedDamage;
        public int timerTicks;
        public int detonateTicks;
        public int immuneTicks;
        public int meleeTimerCdTicks;
    }

    private boolean applyingReducedDamage = false;

    // ── Passive tuning ────────────────────────────────────────────────────────

    private static final int   FATE_TIMER_DEFAULT     = 200;
    private static final int   FATE_TIMER_MAX         = 300;
    private static final float FATE_DAMAGE_CAP        = 60.0f;
    private static final int   FATE_DETONATE_TICKS    = 30;
    private static final int   FATE_DETONATE_INTERVAL = 10;
    private static final float MELEE_FATE_RATIO       = 0.8f;
    private static final int   MELEE_TIMER_ADD        = 12;
    private static final int   MELEE_TIMER_CD         = 10;
    private static final int   FATE_IMMUNE_TICKS      = 200;

    // ── Primary tuning ────────────────────────────────────────────────────────

    private static final double RAY_RANGE         = 30.0;
    private static final float  RAY_DIRECT_DAMAGE = 3.5f;
    private static final float  RAY_FATE_STORE    = 9.0f;
    private static final int    RAY_TIMER_ADD     = 50;

    // ── Secondary tuning ─────────────────────────────────────────────────────

    private static final double STAR_SLAM_RADIUS     = 4.0;
    private static final float  STAR_FATE_STORE      = 14.0f;
    private static final float  STAR_TIMER_REDUCTION = 0.60f;
    private static final int    STAR_ARC_TICKS       = 40;

    // ── Ultimate tuning ───────────────────────────────────────────────────────

    // Changed from 1.5f (150% reduction) to a flat 5 extra ticks drained per game tick
    private static final int    BH_TIMER_REDUCTION = 5;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("cos_"));
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("cos_"));

        player.removeEffect(ModEffects.BRACED);
        player.removeEffect(ModEffects.FATE);

        ACTIVE_STARS.remove(player.getUUID());

        // Instantly remove all targets owned by this player
        Map<UUID, FateInstance> myTargets = ACTIVE_FATES.remove(player.getUUID());
        if (myTargets != null && player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                for (UUID targetId : myTargets.keySet()) {
                    Entity e = w.getEntity(targetId);
                    if (e instanceof LivingEntity le) {
                        le.removeEffect(ModEffects.FATE);
                        // Tell clients to stop rendering
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(le, new FateAuraPayload(le.getId(), 0, 0));
                    }
                }
            }
        }

        // Despawn active black holes
        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                for (BlackHoleEntity bh : w.getEntitiesOfClass(BlackHoleEntity.class, player.getBoundingBox().inflate(150), Entity::isAlive)) {
                    if (player.equals(bh.getOwner())) {
                        bh.discard();
                    }
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

        ServerLevel world = player.serverLevel();
        UUID playerId = player.getUUID();

        // 1. Get ONLY the targets owned by this specific player
        Map<UUID, FateInstance> myTargets = ACTIVE_FATES.get(playerId);

        // If the player has targets, process them
        if (myTargets != null && !myTargets.isEmpty()) {
            Iterator<Map.Entry<UUID, FateInstance>> it = myTargets.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, FateInstance> entry = it.next();
                UUID targetId = entry.getKey();
                FateInstance fate = entry.getValue();

                Entity ent = world.getEntity(targetId);
                if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
                    it.remove();
                    continue;
                }

                // Tick Immunity
                if (fate.immuneTicks > 0) {
                    fate.immuneTicks--;
                    if (fate.immuneTicks <= 0 && fate.storedDamage <= 0) it.remove(); // fully expired
                    continue;
                }

                // Tick Melee CD
                if (fate.meleeTimerCdTicks > 0) {
                    fate.meleeTimerCdTicks--;
                }

                // Tick Detonation
                if (fate.detonateTicks > 0) {
                    if (fate.storedDamage > 0 && target.tickCount % FATE_DETONATE_INTERVAL == 0) {
                        int ticks = FATE_DETONATE_TICKS / FATE_DETONATE_INTERVAL;
                        float damagePerTick = fate.storedDamage / ticks;

                        target.hurt(ModDamageTypes.fate(world, player), damagePerTick);
                        spawnDetonateParticles(world, target);
                    }

                    fate.detonateTicks--;
                    if (fate.detonateTicks <= 0) {
                        fate.storedDamage = 0;
                        fate.immuneTicks = FATE_IMMUNE_TICKS;
                    }
                    continue;
                }

                // Tick Timer
                if (fate.timerTicks > 0) {
                    fate.timerTicks--;
                    if (fate.timerTicks <= 0) {
                        // Tell clients to clear the aura so the explosion looks clean
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new FateAuraPayload(target.getId(), 0, 0));
                        fate.detonateTicks = FATE_DETONATE_TICKS;
                        spawnDetonateStartParticles(world, target);
                    } else {
                        // Server no longer processes particles here, just syncs the effect!
                        syncFateEffect(target, fate.timerTicks);
                    }
                } else if (fate.storedDamage <= 0) {
                    it.remove();
                }
            }
        }

        // Shooting star logic
        if (ACTIVE_STARS.containsKey(playerId)) {
            int ticks = ACTIVE_STARS.get(playerId) - 1;
            if (ticks <= 0) {
                ACTIVE_STARS.remove(playerId);
            } else {
                ACTIVE_STARS.put(playerId, ticks);
                handleShootingStar(player);
            }
        }
    }

    /* ============================================================
       PASSIVE (ATTACK HOOK)
       ============================================================ */

    @Override
    public boolean onAttack(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
        if (!PassiveManager.isEnabled(attacker)) return true;

        if (source.getEntity() != attacker) return true;
        if (this.applyingReducedDamage) return true;

        if (source.is(ModDamageTypes.FATE) || source.is(ModDamageTypes.BLACK_HOLE)) return true;

        applyMeleeFate(attacker, target, amount);

        this.applyingReducedDamage = true;
        target.hurt(source, amount * 0.4f);
        this.applyingReducedDamage = false;

        return false;
    }

    public static void applyMeleeFate(ServerPlayer attacker, LivingEntity target, float damageDealt) {
        float fatePortion = damageDealt * MELEE_FATE_RATIO;
        addFate(target, fatePortion, 0, attacker.getUUID());

        // check if melee CD allows us to add timer
        Map<UUID, FateInstance> playerFates = ACTIVE_FATES.get(attacker.getUUID());
        if (playerFates != null) {
            FateInstance fate = playerFates.get(target.getUUID());
            if (fate != null && fate.meleeTimerCdTicks <= 0) {
                addFate(target, 0, MELEE_TIMER_ADD, attacker.getUUID());
                fate.meleeTimerCdTicks = MELEE_TIMER_CD;
            }
        }
    }

    private static void addFate(LivingEntity target, float fateDmg, int timerAdd, UUID attackerUuid) {
        UUID targetId = target.getUUID();
        FateInstance fate = null;
        UUID previousOwner = null;

        // Find if this target already has fate applied by ANY player
        for (Map.Entry<UUID, Map<UUID, FateInstance>> entry : ACTIVE_FATES.entrySet()) {
            if (entry.getValue().containsKey(targetId)) {
                fate = entry.getValue().get(targetId);
                previousOwner = entry.getKey();
                break;
            }
        }

        if (fate == null) {
            fate = new FateInstance();
        }

        // Handle ownership transfer (if a new attacker hits an already-afflicted target)
        if (attackerUuid != null && !attackerUuid.equals(previousOwner)) {
            if (previousOwner != null) {
                ACTIVE_FATES.get(previousOwner).remove(targetId); // Remove from old owner
            }
            // Add to new owner
            ACTIVE_FATES.computeIfAbsent(attackerUuid, k -> new HashMap<>()).put(targetId, fate);
            fate.ownerUuid = attackerUuid;
        }

        if (fate.immuneTicks > 0) return;

        float currentDmg = fate.storedDamage;
        float newDmg = Math.min(currentDmg + fateDmg, FATE_DAMAGE_CAP);

        boolean hitCapNow = currentDmg < FATE_DAMAGE_CAP && newDmg >= FATE_DAMAGE_CAP;
        boolean alreadyAtCap = currentDmg >= FATE_DAMAGE_CAP && fateDmg > 0;

        fate.storedDamage = newDmg;

        if ((hitCapNow || alreadyAtCap) && target.level() instanceof ServerLevel world) {
            spawnFateCapParticles(world, target);
            playFateCapSound(world, target);
        }

        if (fate.timerTicks <= 0) {
            fate.timerTicks = FATE_TIMER_DEFAULT;
        } else {
            fate.timerTicks = Math.min(fate.timerTicks + timerAdd, FATE_TIMER_MAX);
        }

        // Send payload with the updated stored damage and ticks to start/refresh the visual
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new FateAuraPayload(target.getId(), fate.storedDamage, fate.timerTicks));
        syncFateEffect(target, fate.timerTicks);
    }

    public static void reduceFateTimer(LivingEntity target, float reduction) {
        UUID targetId = target.getUUID();
        FateInstance fate = null;

        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            fate = playerFates.get(targetId);
            if (fate != null) break;
        }

        if (fate == null || fate.timerTicks <= 0 || fate.detonateTicks > 0 || fate.immuneTicks > 0) return;

        // FIXED: Math.max(1, ...) prevents it freezing at 0, allowing onTick to detonate it naturally
        fate.timerTicks = Math.max(1, fate.timerTicks - Math.round(fate.timerTicks * reduction));

        // Notify clients of the timer deduction so the particles stay perfectly synced
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new FateAuraPayload(target.getId(), fate.storedDamage, fate.timerTicks));
        syncFateEffect(target, fate.timerTicks);
    }

    public static void drainFateTimer(LivingEntity target) {
        UUID targetId = target.getUUID();
        FateInstance fate = null;

        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            fate = playerFates.get(targetId);
            if (fate != null) break;
        }

        if (fate == null || fate.timerTicks <= 0 || fate.detonateTicks > 0 || fate.immuneTicks > 0) return;

        // Flat drain per tick makes it visually fast-forward rather than popping instantly
        fate.timerTicks = Math.max(1, fate.timerTicks - BH_TIMER_REDUCTION);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new FateAuraPayload(target.getId(), fate.storedDamage, fate.timerTicks));
        syncFateEffect(target, fate.timerTicks);
    }

    private static void syncFateEffect(LivingEntity entity, int timerTicks) {
        entity.removeEffect(ModEffects.FATE);
        if (timerTicks <= 0) return;

        entity.addEffect(new MobEffectInstance(
                ModEffects.FATE,
                timerTicks,
                0,
                false,
                false,
                true
        ));
    }

    // ── Particle helpers ─────────────────────────────────────────────────────

    private void spawnDetonateStartParticles(ServerLevel world, LivingEntity entity) {
        Vec3 pos = entity.position();
        world.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z, 2, 0.2, 0.2, 0.2, 0);

        for (int i = 0; i < 12; i++) {
            double vx = (world.random.nextDouble() - 0.5) * 0.6;
            double vy = world.random.nextDouble() * 0.6;
            double vz = (world.random.nextDouble() - 0.5) * 0.6;
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 1, pos.z, 0, vx, vy, vz, 1.0);
        }

        world.playSound(null, entity.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_BLAST,
                entity.getSoundSource(), 0.8f, 0.6f);
    }

    private void spawnDetonateParticles(ServerLevel world, LivingEntity entity) {
        if (entity.tickCount % 4 != 0) return;
        Vec3 pos = entity.position();

        for (int i = 0; i < 4; i++) {
            double a = world.getGameTime() * 0.3 + i;
            double x = pos.x + Math.cos(a) * 0.8;
            double z = pos.z + Math.sin(a) * 0.8;
            double y = pos.y + 0.8;
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0,
                    (pos.x - x) * 0.2, 0.02, (pos.z - z) * 0.2, 1.0);
        }

        if (entity instanceof ServerPlayer targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 5, 10, 0.06f);
        }
    }

    private static void spawnFateCapParticles(ServerLevel world, LivingEntity entity) {
        Vec3 pos = entity.position().add(0, entity.getBbHeight() * 0.6, 0);

        for (int i = 0; i < 16; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.4 + world.random.nextDouble() * 0.6;
            world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0,
                    Math.cos(a) * speed,
                    (world.random.nextDouble() - 0.3) * 0.6,
                    Math.sin(a) * speed, 1.0);
        }
    }

    private static void playFateCapSound(ServerLevel world, LivingEntity entity) {
        world.playSound(null, entity.blockPosition(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE,
                entity.getSoundSource(), 0.8f, 1.8f);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 end    = origin.add(look.scale(RAY_RANGE));

        var blockHit = world.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        spawnCosmicRayParticles(world, origin, end);

        LivingEntity firstTarget = getEntityOnBeam(world, player, origin, end);

        for (LivingEntity e : world.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(origin, end).inflate(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().inflate(0.3).clip(origin, end);
            if (hit.isEmpty()) continue;

            e.hurt(ModDamageTypes.cosmicRay(world, player), RAY_DIRECT_DAMAGE);
            addFate(e, RAY_FATE_STORE, RAY_TIMER_ADD, player.getUUID());

            Vec3 hitPos = hit.get();
            world.sendParticles(ParticleTypes.FLASH,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.05, 0.05, 0.05, 0);

            world.sendParticles(ParticleTypes.END_ROD,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.2, 0.2, 0.2, 0.02);
        }

        world.playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_BLAST,
                player.getSoundSource(), 0.4f, 1.6f);
        world.playSound(null, player.blockPosition(),
                ModSounds.COSMICRAY.get(),
                player.getSoundSource(), 0.4f, 1.6f);

        player.swing(InteractionHand.MAIN_HAND, true);

        if (firstTarget != null) {
            Vec3 hitPos = firstTarget.getBoundingBox().getCenter();
            world.sendParticles(ParticleTypes.FLASH,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.1, 0.1, 0.1, 0);

            for (int i = 0; i < 4; i++) {
                world.sendParticles(ParticleTypes.END_ROD,
                        hitPos.x, hitPos.y, hitPos.z, 0,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        world.random.nextDouble() * 0.3,
                        (world.random.nextDouble() - 0.5) * 0.4, 1.0);
            }
        }
    }

    private void spawnCosmicRayParticles(ServerLevel world, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();

        Vec3 step = dir.normalize().scale(0.6);
        int count = (int)(len / 0.6);
        Vec3 pos = from;

        for (int i = 0; i < count; i++) {
            double angle = i * 0.4;
            double spiral = 0.08;
            double x = pos.x + Math.cos(angle) * spiral;
            double y = pos.y;
            double z = pos.z + Math.sin(angle) * spiral;

            world.sendParticles(
                    new DustParticleOptions(new Vector3f(0.4f, 0.0f, 0.6f), 1.6f),
                    x, y, z, 1, 0, 0, 0, 0);

            if (i % 3 == 0) {
                world.sendParticles(ParticleTypes.SMOKE, x, y, z, 1,
                        0.01, 0.01, 0.01, 0.01);
            }
            if (world.random.nextFloat() < 0.15f) {
                world.sendParticles(ParticleTypes.END_ROD, x, y, z, 1,
                        0.02, 0.02, 0.02, 0.01);
            }

            pos = pos.add(step);
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        player.setDeltaMovement(0, 1.2, 0);
        player.hurtMarked = true;
        player.hasImpulse = true;

        ACTIVE_STARS.put(player.getUUID(), STAR_ARC_TICKS);

        world.playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH,
                player.getSoundSource(), 0.9f, 0.8f);

        player.addEffect(new MobEffectInstance(ModEffects.BRACED, 100, 0, true, false, true));
    }

    private void handleShootingStar(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        spawnStarTrailParticles(world, player);

        Vec3 velocity = player.getDeltaMovement();

        if (velocity.y > 0) {
            Vec3 look = player.getViewVector(1.0f);
            player.setDeltaMovement(player.getDeltaMovement().add(look.x * 0.02, 0, look.z * 0.02));
            player.hurtMarked = true;
            player.hasImpulse = true;
            return;
        }

        Vec3 look = player.getViewVector(1.0f);
        player.setDeltaMovement(look.x * 1.2, -1.5, look.z * 1.2);
        player.hurtMarked = true;
        player.hasImpulse = true;

        boolean hitGround = player.onGround() || player.verticalCollision;
        if (!hitGround) return;

        Vec3 slamPos = player.position();

        for (LivingEntity e : world.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(STAR_SLAM_RADIUS),
                en -> en.isAlive() && en != player)) {

            if (e.position().distanceTo(slamPos) > STAR_SLAM_RADIUS) continue;

            e.hurt(ModDamageTypes.shootingStar(world, player), 4.0f);
            addFate(e, STAR_FATE_STORE, 0, player.getUUID());
            reduceFateTimer(e, STAR_TIMER_REDUCTION);
        }

        spawnSlamParticles(world, slamPos);

        world.playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(), 1.0f, 0.8f);

        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true;
        player.hasImpulse = true;
        ACTIVE_STARS.remove(player.getUUID());
    }

    private void spawnStarTrailParticles(ServerLevel world, ServerPlayer player) {
        Vec3 pos = player.position();

        world.sendParticles(ParticleTypes.END_ROD,
                pos.x, pos.y + 0.5, pos.z, 0,
                (world.random.nextDouble() - 0.5) * 0.2,
                0.05,
                (world.random.nextDouble() - 0.5) * 0.2,
                1.0);
    }

    private void spawnSlamParticles(ServerLevel world, Vec3 pos) {
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2) * i / 16;
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.1, pos.z, 0,
                    Math.cos(angle) * 0.4, 0.1, Math.sin(angle) * 0.4, 1.0);
        }
        world.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 0.5, pos.z, 2, 0.2, 0.2, 0.2, 0);
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        Vec3 lookDir = player.getViewVector(1.0f).normalize();
        Vec3 spawnPos = player.getEyePosition().add(lookDir.scale(3.0));

        BlackHoleEntity bh = new BlackHoleEntity(ModEntities.BLACK_HOLE_ENTITY.get(), world);
        bh.setOwner(player);
        bh.setPos(spawnPos.x, spawnPos.y - 1.0, spawnPos.z);
        bh.setTravelDirection(lookDir);
        world.addFreshEntity(bh);

        world.playSound(null, player.blockPosition(),
                SoundEvents.PORTAL_AMBIENT,
                player.getSoundSource(), 1.2f, 0.4f);
        world.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM,
                player.getSoundSource(), 0.6f, 0.5f);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public long getPrimaryCooldownMs()   { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 22_000; }
    @Override public long getUltimateCooldownMs()  { return 250_000; }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.cosmic.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.cosmic.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.cosmic.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.cosmic.ultimate_name").getString(); }
    @Override public String getPassiveName() { return Component.translatable("power.loopypowers.cosmic.passive_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.cosmic.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.cosmic.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.cosmic.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.cosmic.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.cosmic.description.ultimate").getString();
    }

    private LivingEntity getEntityOnBeam(ServerLevel world, ServerPlayer player, Vec3 origin, Vec3 end) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, end).inflate(1.0),
                en -> en.isAlive() && en != player)) {
            var hit = e.getBoundingBox().inflate(0.3).clip(origin, end);
            if (hit.isPresent()) {
                double dist = origin.distanceToSqr(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    // OTHER HELPERS

    public static boolean cleanseFate(LivingEntity target) {
        boolean cleansed = false;
        UUID targetId = target.getUUID();

        // Loop through every attacker's active fates and remove this target
        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            if (playerFates.remove(targetId) != null) {
                cleansed = true;
            }
        }

        if (cleansed) {
            target.removeEffect(ModEffects.FATE);
            // Tell clients to immediately stop rendering the cosmic aura
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new FateAuraPayload(target.getId(), 0, 0));
            return true;
        }

        return false;
    }
}