package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.BlackHoleEntity;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    private static final float  BH_TIMER_REDUCTION = 1.5f;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("cos_")); // getCommandTags -> getTags
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
            for (ServerLevel w : player.getServer().getAllLevels()) { // getWorlds -> getAllLevels
                for (UUID targetId : myTargets.keySet()) {
                    Entity e = w.getEntity(targetId);
                    if (e instanceof LivingEntity le) le.removeEffect(ModEffects.FATE);
                }
            }
        }

        // Despawn active black holes
        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                // getEntitiesByClass -> getEntitiesOfClass, Box -> AABB
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
                    if (fate.storedDamage > 0 && target.tickCount % FATE_DETONATE_INTERVAL == 0) { // age -> tickCount
                        int ticks = FATE_DETONATE_TICKS / FATE_DETONATE_INTERVAL;
                        float damagePerTick = fate.storedDamage / ticks;

                        target.hurt(ModDamageTypes.fate(world, player), damagePerTick); // damage -> hurt
                        spawnDetonateParticles(world, target);
                    }

                    fate.detonateTicks--;
                    if (fate.detonateTicks <= 0) {
                        fate.storedDamage = 0;
                        fate.immuneTicks = FATE_IMMUNE_TICKS;
                        // target.removeEffect(ModEffects.FATE);
                    }
                    continue;
                }

                // Tick Timer
                if (fate.timerTicks > 0) {
                    fate.timerTicks--;
                    if (fate.timerTicks <= 0) {
                        fate.detonateTicks = FATE_DETONATE_TICKS;
                        spawnDetonateStartParticles(world, target);
                        // target.removeEffect(ModEffects.FATE);
                    } else {
                        spawnFateAuraParticles(world, target, fate);
                        syncFateEffect(target, fate.timerTicks);
                    }
                } else if (fate.storedDamage <= 0) {
                    // If it has no timer, no immunity, no detonation, and no damage, clean it up
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

        if (source.getEntity() != attacker) return true; // getSource() -> getEntity()
        if (this.applyingReducedDamage) return true;

        if (source.is(ModDamageTypes.FATE) || source.is(ModDamageTypes.BLACK_HOLE)) return true; // isOf -> is

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

        syncFateEffect(target, fate.timerTicks);
    }

    public static void reduceFateTimer(LivingEntity target, float reduction) {
        UUID targetId = target.getUUID();
        FateInstance fate = null;

        // Find fate instance regardless of who owns it
        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            fate = playerFates.get(targetId);
            if (fate != null) break;
        }

        if (fate == null || fate.timerTicks <= 0 || fate.detonateTicks > 0 || fate.immuneTicks > 0) return;

        fate.timerTicks = Math.max(0, fate.timerTicks - Math.round(fate.timerTicks * reduction));
        syncFateEffect(target, fate.timerTicks);
    }

    // ── Status-effect sync ────────────────────────────────────────────────────
    private static void syncFateEffect(LivingEntity entity, int timerTicks) {
        entity.removeEffect(ModEffects.FATE);
        if (timerTicks <= 0) return;

        entity.addEffect(new MobEffectInstance(
                 ModEffects.FATE,
                timerTicks,
                 0,
                 false,
                 false,      // hide particles
                        true        // show icon in HUD
         ));
    }

    // ── Particle helpers ─────────────────────────────────────────────────────

    private void spawnFateAuraParticles(ServerLevel world, LivingEntity entity, FateInstance fate) {
        int starCount = Mth.clamp((int)fate.storedDamage, 0, 19); // MathHelper -> Mth
        long time     = entity.level().getGameTime(); // getWorld().getTime() -> level().getGameTime()

        float  timerFraction = (float) fate.timerTicks / FATE_TIMER_MAX;
        double speed         = 0.05 + (1.0 - timerFraction) * 0.35;
        double angle         = time * speed;
        double orbitRadius   = 0.8 + 0.35;
        int    trailPoints   = (int)(3 + speed * 10);

        for (int t = 0; t < trailPoints; t++) {
            double trailAngle = angle - (t * 0.15);
            double cx   = entity.getX() + Math.cos(trailAngle) * orbitRadius;
            double cz   = entity.getZ() + Math.sin(trailAngle) * orbitRadius;
            double cy   = entity.getY(0.55); // getBodyY -> getY
            float  size = 1.2f - (t * 0.08f);

            world.sendParticles(
                    new DustParticleOptions(new Vector3f(1.0f, 0.5f, 0.1f), Math.max(0.4f, size)),
                    cx, cy, cz, 1, 0, 0, 0, 0);
        }

        if (timerFraction < 0.3f) {
            int flameCount = (int)((0.3f - timerFraction) * 20);
            for (int i = 0; i < flameCount; i++) {
                double randAngle = world.random.nextDouble() * Math.PI * 2;
                double r  = orbitRadius + 0.1 + world.random.nextDouble() * 0.2;
                double fx = entity.getX() + Math.cos(randAngle) * r;
                double fz = entity.getZ() + Math.sin(randAngle) * r;
                double fy = entity.getY(0.55) + world.random.nextDouble() * 0.3;
                world.sendParticles(ParticleTypes.FLAME, fx, fy, fz, 1,
                        0.01, 0.02, 0.01, 0.01);
            }
        }

        if (entity.tickCount % 3 != 0) return;

        for (int i = 0; i < starCount; i++) {
            long seed      = entity.getId() * 341873128712L + i * 132897987541L;
            double offsetX = ((seed >> 16) & 0xFF) / 255.0 * 2.0 - 1.0;
            double offsetY = ((seed >> 8)  & 0xFF) / 255.0;
            double offsetZ = ((seed)       & 0xFF) / 255.0 * 2.0 - 1.0;
            world.sendParticles(ParticleTypes.WHITE_ASH,
                    entity.getX() + offsetX * 0.8,
                    entity.getY(offsetY * 1.2),
                    entity.getZ() + offsetZ * 0.8,
                    1, 0.01, 0.02, 0.01, 0.001);
        }
    }

    private void spawnDetonateStartParticles(ServerLevel world, LivingEntity entity) {
        Vec3 pos = entity.position(); // getPos -> position
        world.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z, 3, 0.2, 0.2, 0.2, 0);
        for (int i = 0; i < 25; i++) {
            double vx = (world.random.nextDouble() - 0.5) * 0.6;
            double vy = world.random.nextDouble() * 0.6;
            double vz = (world.random.nextDouble() - 0.5) * 0.6;
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 1, pos.z, 1, vx, vy, vz, 0.1);
        }

        world.playSound(null, entity.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_BLAST,
                entity.getSoundSource(), 0.8f, 0.6f);
    }

    private void spawnDetonateParticles(ServerLevel world, LivingEntity entity) {
        if (entity.tickCount % 2 != 0) return;
        Vec3 pos = entity.position();
        for (int i = 0; i < 6; i++) {
            double a = world.getGameTime() * 0.3 + i;
            double x = pos.x + Math.cos(a) * 0.8;
            double z = pos.z + Math.sin(a) * 0.8;
            double y = pos.y + 0.8;
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1,
                    (pos.x - x) * 0.2, 0.02, (pos.z - z) * 0.2, 0);
        }

        if (entity instanceof ServerPlayer targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 5, 10, 0.06f);
        }
    }

    private static void spawnFateCapParticles(ServerLevel world, LivingEntity entity) {
        Vec3 pos = entity.position().add(0, entity.getBbHeight() * 0.6, 0); // getHeight -> getBbHeight
        for (int i = 0; i < 40; i++) {
            double a     = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.4 + world.random.nextDouble() * 0.6;
            world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1,
                    Math.cos(a) * speed,
                    (world.random.nextDouble() - 0.3) * 0.6,
                    Math.sin(a) * speed, 0.05);
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
        Vec3 end    = origin.add(look.scale(RAY_RANGE)); // multiply -> scale

        var blockHit = world.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation(); // getPos() -> getLocation()
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
                    hitPos.x, hitPos.y, hitPos.z, 3, 0.2, 0.2, 0.2, 0.02);
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
                    hitPos.x, hitPos.y, hitPos.z, 2, 0.1, 0.1, 0.1, 0);
            for (int i = 0; i < 8; i++) {
                world.sendParticles(ParticleTypes.END_ROD,
                        hitPos.x, hitPos.y, hitPos.z, 1,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        world.random.nextDouble() * 0.3,
                        (world.random.nextDouble() - 0.5) * 0.4, 0.05);
            }
        }
    }

    private void spawnCosmicRayParticles(ServerLevel world, Vec3 from, Vec3 to) {
        Vec3 dir  = to.subtract(from);
        double len = dir.length();
        Vec3 step = dir.normalize().scale(0.25);
        int count  = (int)(len / 0.25);
        Vec3 pos  = from;

        for (int i = 0; i < count; i++) {
            double angle  = i * 0.4;
            double spiral = 0.08;
            double x = pos.x + Math.cos(angle) * spiral;
            double y = pos.y;
            double z = pos.z + Math.sin(angle) * spiral;

            world.sendParticles(
                    new DustParticleOptions(new Vector3f(0.4f, 0.0f, 0.6f), 1.1f),
                    x, y, z, 1, 0, 0, 0, 0);

            if (i % 2 == 0) {
                world.sendParticles(ParticleTypes.SMOKE, x, y, z, 1,
                        0.01, 0.01, 0.01, 0.01);
            }
            if (world.random.nextFloat() < 0.25f) {
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

        player.setDeltaMovement(0, 1.2, 0); // setVelocity -> setDeltaMovement
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
            player.setDeltaMovement(player.getDeltaMovement().add(look.x * 0.02, 0, look.z * 0.02)); // addVelocity -> setDeltaMovement(add)
            return;
        }

        Vec3 look = player.getViewVector(1.0f);
        player.setDeltaMovement(look.x * 1.2, -1.5, look.z * 1.2);
        player.hasImpulse = true;

        boolean hitGround = player.onGround() || player.verticalCollision; // isOnGround -> onGround
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
        player.hasImpulse = true;
        ACTIVE_STARS.remove(player.getUUID());
    }

    private void spawnStarTrailParticles(ServerLevel world, ServerPlayer player) {
        Vec3 pos = player.position();
        for (int i = 0; i < 3; i++) {
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.5, pos.z, 1,
                    (world.random.nextDouble() - 0.5) * 0.2,
                    0.05,
                    (world.random.nextDouble() - 0.5) * 0.2,
                    0.02);
        }
    }

    private void spawnSlamParticles(ServerLevel world, Vec3 pos) {
        for (int i = 0; i < 30; i++) {
            double angle = (Math.PI * 2) * i / 30;
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.1, pos.z, 1,
                    Math.cos(angle) * 0.4, 0.1, Math.sin(angle) * 0.4, 0.05);
        }
        world.sendParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 0.5, pos.z, 3, 0.2, 0.2, 0.2, 0);
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
        world.addFreshEntity(bh); // spawnEntity -> addFreshEntity

        world.playSound(null, player.blockPosition(),
                SoundEvents.PORTAL_AMBIENT,
                player.getSoundSource(), 1.2f, 0.4f);
        world.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM,
                player.getSoundSource(), 0.6f, 0.5f);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void drainFateTimer(LivingEntity target) {
        reduceFateTimer(target, BH_TIMER_REDUCTION);
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
                double dist = origin.distanceToSqr(hit.get()); // squaredDistanceTo -> distanceToSqr
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }
}