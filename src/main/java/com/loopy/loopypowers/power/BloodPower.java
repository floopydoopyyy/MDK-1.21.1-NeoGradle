package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.BloodBindPayload;
import com.loopy.loopypowers.network.payload.BloodWhipPayload;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.entity.BloodClotEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

import java.util.*;

public class BloodPower implements PowerInterface {

    private static final Random RNG = new Random();

    /* ============================================================
       CONSTANTS & TUNING
       ============================================================ */

    // -- PASSIVE --
    public static final float BLEED_FRACTION = 0.20f;
    public static final int BLEED_DURATION_TICKS = 60;
    public static final int BLEED_TICK_INTERVAL = 15;

    // -- PRIMARY --
    public static final double WHIP_RANGE = 17.0;
    public static final float WHIP_SELF_DAMAGE = 2.0f;
    public static final float WHIP_BLEED_DAMAGE = 8.0f;
    public static final int WHIP_BLEED_DURATION = 60;
    public static final int WHIP_BLEED_INTERVAL = 15;
    public static final double WHIP_YANK_XZ = 1.3;
    public static final double WHIP_YANK_Y = 0.4;

    // -- SECONDARY --
    public static final double CLOT_SPEED = 0.75;
    public static final int CLOT_LIFETIME_TICKS = 60;
    public static final float CLOT_SELF_DAMAGE = 4.0f;
    public static final int CLOT_SLOW_TICKS = 80;
    public static final int CLOT_WEAK_TICKS = 80;
    public static final float CLOT_HIT_DAMAGE = 10.0f;

    public static final float CLOT_BLEED_DAMAGE = 10.0f;
    public static final int CLOT_BLEED_DURATION = 60;
    public static final int CLOT_BLEED_INTERVAL = 15;

    public static final float POP_DAMAGE_MULT = 6.0f;
    public static final int POP_DURATION = 10;
    public static final int POP_INTERVAL = 5;
    public static final float POP_HEAL_MULT = 0.75f;

    // -- ULTIMATE --
    private static final double BIND_CAST_RANGE = 24.0;
    private static final double BIND_MAX_RANGE = 20.0;
    private static final int BIND_DURATION_TICKS = 300;
    private static final float BIND_DAMAGE_REDUCTION = 0.60f;
    private static final float BIND_DAMAGE_SHARE = 0.60f;
    private static final int ENV_APPLY_INTERVAL_TICKS = 8;
    private static final float ENV_MAX_CHUNK = 8.0f;
    private static final int BIND_HIT_FX_COOLDOWN_TICKS = 6;

    private static final DustParticleOptions BLOOD_DUST =
            new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.05f), 1.25f);

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {}

    @Override
    public void onRemove(ServerPlayer player) {
        breakBind(player);
        PENDING_ENV_DAMAGE.remove(player.getUUID());
        LAST_ENV_APPLY.remove(player.getUUID());
        LAST_BIND_HIT_FX.remove(player.getUUID());
        player.getTags().remove(BIND_GUARD);
    }

    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;
        tickBleed(player);
        tickBind(player);
    }

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    protected static final Map<UUID, BleedInstance> ACTIVE_BLEEDS = new HashMap<>();

    public static class BleedInstance {
        public UUID attackerUuid;
        public int ticksLeft;
        public int nextTick;
        public float perTickDmg;
        public float totalDmgLeft;
    }

    private static long lastBleedTickTime = Long.MIN_VALUE;
    private static final String BIND_GUARD = "bl_bind_guard";

    /* ============================================================
       PASSIVE (ATTACK HOOK)
       ============================================================ */

    public boolean onAttack(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
        if (!PassiveManager.isEnabled(attacker)) return true;

        if (target == attacker || !target.isAlive() || amount <= 0) return true;
        if (source.getEntity() != attacker) return true;

        if (source.is(ModDamageTypes.BLEED) || source.is(ModDamageTypes.BIND)) {
            return true;
        }

        target.addEffect(new MobEffectInstance(ModEffects.BLEED, BLEED_DURATION_TICKS, 0, true, false));

        float totalBleed = amount * BLEED_FRACTION;
        int intervals = Math.max(1, BLEED_DURATION_TICKS / BLEED_TICK_INTERVAL);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUUID();
        BleedInstance b = ACTIVE_BLEEDS.computeIfAbsent(id, k -> new BleedInstance());

        b.attackerUuid = attacker.getUUID();
        b.ticksLeft = BLEED_DURATION_TICKS;
        b.nextTick = BLEED_TICK_INTERVAL;
        b.perTickDmg = perTick;
        b.totalDmgLeft = totalBleed;

        if (attacker.level() instanceof ServerLevel w) {
            w.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    4, 0.25, 0.35, 0.25, 0.02);
            w.playSound(null, target.blockPosition(),
                    SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                    attacker.getSoundSource(), 0.35f, 0.8f);
        }

        return true;
    }

    private void tickBleed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();
        if (lastBleedTickTime == now) return;
        lastBleedTickTime = now;

        if (ACTIVE_BLEEDS.isEmpty()) return;

        var it = ACTIVE_BLEEDS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID victimUuid = entry.getKey();
            BleedInstance b = entry.getValue();

            b.ticksLeft--;
            b.nextTick--;

            if (b.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            if (b.nextTick > 0) continue;
            b.nextTick = BLEED_TICK_INTERVAL;

            LivingEntity victim = null;
            for (ServerLevel w : server.getAllLevels()) {
                Entity e = w.getEntity(victimUuid);
                if (e instanceof LivingEntity le) {
                    victim = le;
                    break;
                }
            }
            if (victim == null || !victim.isAlive()) {
                it.remove();
                continue;
            }

            ServerPlayer attacker = (b.attackerUuid != null)
                    ? server.getPlayerList().getPlayer(b.attackerUuid)
                    : null;

            DamageSource bleedSrc = ModDamageTypes.bleed(victim.level());
            victim.hurt(bleedSrc, b.perTickDmg);
            b.totalDmgLeft -= b.perTickDmg;

            if (attacker != null && attacker.isAlive()) {
                attacker.heal(b.perTickDmg);
            }

            ServerLevel w = (ServerLevel) victim.level();
            w.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    6, 0.30, 0.40, 0.30, 0.02);
        }
    }


    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        player.hurt(ModDamageTypes.bloodself(world, player), WHIP_SELF_DAMAGE);

        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0f).normalize();
        Vec3 end = start.add(dir.scale(WHIP_RANGE));

        HitResult blockHit = world.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getLocation() : end;

        LivingEntity hitEntity = null;
        Vec3 hitPos = null;
        double bestDistSq = start.distanceToSqr(blockEnd);

        AABB searchBox = new AABB(start, blockEnd).inflate(1.2);

        List<LivingEntity> candidates = world.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity e : candidates) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.25).clip(start, blockEnd);
            if (hit.isEmpty()) continue;

            Vec3 p = hit.get();
            double d = start.distanceToSqr(p);

            if (d < bestDistSq) {
                bestDistSq = d;
                hitEntity = e;
                hitPos = p;
            }
        }

        Vec3 beamEnd = (hitPos != null) ? hitPos : blockEnd;

        // Trigger Client Payload for the Whip Visuals
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new BloodWhipPayload(player.getId(), beamEnd.x, beamEnd.y, beamEnd.z));

        world.playSound(null, player.blockPosition(),
                SoundEvents.IRON_GOLEM_DAMAGE,
                player.getSoundSource(), 0.75f, 1.5f
        );
        player.swing(InteractionHand.MAIN_HAND, true);

        if (hitEntity != null) {
            applyBleedFromProjectile(player, hitEntity, WHIP_BLEED_DAMAGE, WHIP_BLEED_DURATION, WHIP_BLEED_INTERVAL);

            Vec3 pullDir = player.position().subtract(hitEntity.position()).normalize();

            hitEntity.setDeltaMovement(hitEntity.getDeltaMovement().add(pullDir.x * WHIP_YANK_XZ, WHIP_YANK_Y, pullDir.z * WHIP_YANK_XZ));
            hitEntity.hasImpulse = true;

            world.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    hitEntity.getX(), hitEntity.getY() + 1.0, hitEntity.getZ(),
                    10, 0.35, 0.45, 0.35, 0.02);

            world.playSound(null, hitEntity.blockPosition(),
                    SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                    player.getSoundSource(), 0.7f, 0.9f
            );
        }
    }


    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        BloodClotEntity clot = new BloodClotEntity(ModEntities.BLOOD_CLOT.get(), world);
        clot.setOwner(player);
        clot.setTuning(CLOT_LIFETIME_TICKS, CLOT_SLOW_TICKS, CLOT_WEAK_TICKS, CLOT_HIT_DAMAGE);
        clot.setPos(player.getX(), player.getEyeY(), player.getZ());
        clot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, (float) CLOT_SPEED, 0.0f);
        clot.hasImpulse = true;

        world.addFreshEntity(clot);

        player.swing(InteractionHand.MAIN_HAND, true);
        player.hurt(ModDamageTypes.bloodself(world, player), CLOT_SELF_DAMAGE);

        world.sendParticles(BLOOD_DUST,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.25, 0.30, 0.25, 0.02);

        world.playSound(null, player.blockPosition(),
                SoundEvents.SLIME_SQUISH,
                player.getSoundSource(), 0.65f, 0.6f
        );
    }

    public static void applyBleedFromProjectile(LivingEntity attacker, LivingEntity target, float totalBleed, int durationTicks, int intervalTicks) {
        if (attacker == null || target == null) return;
        if (!target.isAlive()) return;
        if (target == attacker) return;

        int intervals = Math.max(1, durationTicks / intervalTicks);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUUID();

        BleedInstance b = ACTIVE_BLEEDS.computeIfAbsent(id, k -> new BleedInstance());
        b.attackerUuid = (attacker instanceof ServerPlayer sp) ? sp.getUUID() : null;
        b.ticksLeft = durationTicks;
        b.nextTick = intervalTicks + attacker.getRandom().nextInt(8);
        b.perTickDmg = perTick;
        b.totalDmgLeft = totalBleed;

        target.addEffect(new MobEffectInstance(ModEffects.BLEED, durationTicks, 0, true, false));

        if (attacker.level() instanceof ServerLevel w) {
            w.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    3, 0.20, 0.25, 0.20, 0.01);
            w.playSound(null, target.blockPosition(),
                    SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                    attacker.getSoundSource(), 0.25f, 0.9f);
        }
    }

    public static void popBleed(LivingEntity target, ServerPlayer attacker) {
        if (target == null) return;

        UUID id = target.getUUID();
        BleedInstance b = ACTIVE_BLEEDS.remove(id);

        target.removeEffect(ModEffects.BLEED);

        if (b == null || b.totalDmgLeft <= 0) return;

        float totalPopDamage = b.totalDmgLeft * POP_DAMAGE_MULT;

        applyBleedFromProjectile(attacker, target, totalPopDamage, POP_DURATION, POP_INTERVAL);
        attacker.heal(totalPopDamage * POP_HEAL_MULT);

        if (attacker.level() instanceof ServerLevel w) {
            w.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, attacker.getSoundSource(), 1.0f, 1.2f);

            w.sendParticles(new DustParticleOptions(new Vector3f(0.8f, 0.0f, 0.0f), 2.5f),
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    80, 0.4, 0.6, 0.4, 0.15);

            w.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    40, 0.5, 0.5, 0.5, 0.1);

            w.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

            CameraShake.shakeNearby(attacker, 3.0, 10, 0.4f);
        }
    }

    public static boolean isBleeding(LivingEntity target) {
        return ACTIVE_BLEEDS.containsKey(target.getUUID());
    }

    /* ============================================================
       ULTIMATE (VICTIM HOOK)
       ============================================================ */

    private static final Map<UUID, BindInstance> ACTIVE_BINDS = new HashMap<>();

    private static class BindInstance {
        public UUID targetUuid;
        public int ticksLeft;
    }

    private static long lastBindTickTime = Long.MIN_VALUE;
    private static final Map<UUID, Long> LAST_BIND_HIT_FX = new HashMap<>();
    private static final Map<UUID, Float> PENDING_ENV_DAMAGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_ENV_APPLY = new HashMap<>();

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        if (isBound(player)) {
            breakBind(player);
            world.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, player.getSoundSource(), 0.8f, 1.0f);
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, true);

        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0f).normalize();
        Vec3 end = start.add(dir.scale(BIND_CAST_RANGE));

        HitResult blockHit = world.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        Vec3 blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getLocation() : end;

        LivingEntity hitEntity = null;
        Vec3 hitPos = null;
        double bestDistSq = start.distanceToSqr(blockEnd);

        AABB searchBox = new AABB(start, blockEnd).inflate(1.2);
        List<LivingEntity> candidates = world.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e.isAlive() && e != player);

        for (LivingEntity e : candidates) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.25).clip(start, blockEnd);
            if (hit.isEmpty()) continue;

            Vec3 p = hit.get();
            double d = start.distanceToSqr(p);
            if (d < bestDistSq) {
                bestDistSq = d;
                hitEntity = e;
                hitPos = p;
            }
        }

        Vec3 beamEnd = (hitPos != null) ? hitPos : blockEnd;

        spawnBloodChain(world, start, beamEnd);

        world.playSound(null, player.blockPosition(),
                SoundEvents.CHAIN_HIT,
                player.getSoundSource(), 0.9f, 0.9f
        );

        if (hitEntity == null) {
            world.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, player.getSoundSource(), 0.6f, 0.6f);
            return;
        }

        startBind(player, hitEntity);

        world.playSound(null, hitEntity.blockPosition(),
                SoundEvents.CHAIN_PLACE,
                player.getSoundSource(), 1.0f, 1.0f
        );
    }

    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        if (amount <= 0) return true;

        BindInstance b = ACTIVE_BINDS.get(victim.getUUID());
        if (b == null) return true;

        if (victim.getTags().contains(BIND_GUARD)) return true;

        MinecraftServer server = victim.getServer();
        if (server == null) return true;

        LivingEntity target = null;
        for (ServerLevel w : server.getAllLevels()) {
            Entity e = w.getEntity(b.targetUuid);
            if (e instanceof LivingEntity le) { target = le; break; }
        }

        if (target == null || !target.isAlive() || victim.distanceToSqr(target) > (BIND_MAX_RANGE * BIND_MAX_RANGE)) {
            breakBind(victim);
            return true;
        }

        if (source.getEntity() == null && victim.level() instanceof ServerLevel sw) {
            long now = sw.getGameTime();
            float pending = PENDING_ENV_DAMAGE.getOrDefault(victim.getUUID(), 0.0f) + amount;
            if (pending > ENV_MAX_CHUNK) pending = ENV_MAX_CHUNK;
            PENDING_ENV_DAMAGE.put(victim.getUUID(), pending);

            long last = LAST_ENV_APPLY.getOrDefault(victim.getUUID(), Long.MIN_VALUE);
            if ((now - last) < ENV_APPLY_INTERVAL_TICKS) {
                return false;
            }

            LAST_ENV_APPLY.put(victim.getUUID(), now);
            amount = pending;
            PENDING_ENV_DAMAGE.remove(victim.getUUID());
        }

        float reduced = amount * (1.0f - BIND_DAMAGE_REDUCTION);
        float shared  = amount * BIND_DAMAGE_SHARE;

        victim.getTags().add(BIND_GUARD);
        if (target instanceof ServerPlayer spTarget) spTarget.getTags().add(BIND_GUARD);

        try {
            if (reduced > 0.0f) victim.hurt(source, reduced);
            DamageSource bindSrc = ModDamageTypes.bind(target.level());
            if (shared > 0.0f) target.hurt(bindSrc, shared);

            if (victim.level() instanceof ServerLevel sw) {
                long now = sw.getGameTime();
                long last = LAST_BIND_HIT_FX.getOrDefault(victim.getUUID(), Long.MIN_VALUE);

                if ((now - last) >= BIND_HIT_FX_COOLDOWN_TICKS) {
                    LAST_BIND_HIT_FX.put(victim.getUUID(), now);
                    spawnBindDamageFx(sw, victim, target, amount);
                }
            }
        } finally {
            victim.getTags().remove(BIND_GUARD);
            if (target instanceof ServerPlayer spTarget) spTarget.getTags().remove(BIND_GUARD);
        }

        return false;
    }

    private void tickBind(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();
        if (lastBindTickTime == now) return;
        lastBindTickTime = now;

        if (ACTIVE_BINDS.isEmpty()) return;

        var it = ACTIVE_BINDS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID casterUuid = entry.getKey();
            BindInstance b = entry.getValue();

            b.ticksLeft--;
            if (b.ticksLeft <= 0) {
                ServerPlayer caster = server.getPlayerList().getPlayer(casterUuid);
                if (caster != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new BloodBindPayload(caster.getId(), -1, false));
                it.remove();
                continue;
            }

            ServerPlayer caster = server.getPlayerList().getPlayer(casterUuid);
            if (caster == null || !caster.isAlive()) {
                if (caster != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new BloodBindPayload(caster.getId(), -1, false));
                it.remove();
                if (caster != null) {
                    PENDING_ENV_DAMAGE.remove(caster.getUUID());
                    LAST_ENV_APPLY.remove(caster.getUUID());
                }
                continue;
            }

            LivingEntity target = null;
            for (ServerLevel w : server.getAllLevels()) {
                Entity e = w.getEntity(b.targetUuid);
                if (e instanceof LivingEntity le) { target = le; break; }
            }

            if (target == null || !target.isAlive()) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new BloodBindPayload(caster.getId(), -1, false));
                it.remove();
                PENDING_ENV_DAMAGE.remove(caster.getUUID());
                LAST_ENV_APPLY.remove(caster.getUUID());
                continue;
            }

            double max = BIND_MAX_RANGE * BIND_MAX_RANGE;
            if (caster.distanceToSqr(target) > max) {
                caster.serverLevel().playSound(null, caster.blockPosition(),
                        SoundEvents.CHAIN_BREAK,
                        caster.getSoundSource(),
                        0.9f, 1.0f
                );
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new BloodBindPayload(caster.getId(), -1, false));
                it.remove();
                PENDING_ENV_DAMAGE.remove(caster.getUUID());
                LAST_ENV_APPLY.remove(caster.getUUID());
                continue;
            }

            double dist = Math.sqrt(caster.distanceToSqr(target));
            float strain = (float) Mth.clamp((dist - 10.0) / (BIND_MAX_RANGE - 10.0), 0.0, 1.0);

            if (strain > 0.55f) {
                if ((b.ticksLeft % 2) == 0) {
                    caster.serverLevel().sendParticles(ParticleTypes.CRIT,
                            caster.getX(), caster.getY() + 1.0, caster.getZ(),
                            1, 0.15, 0.15, 0.15, 0.0);

                    caster.serverLevel().sendParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY() + 1.0, target.getZ(),
                            1, 0.15, 0.15, 0.15, 0.0);
                }

                if ((b.ticksLeft % 10) == 0) {
                    caster.serverLevel().playSound(null, caster.blockPosition(),
                            SoundEvents.CHAIN_HIT,
                            caster.getSoundSource(),
                            0.35f,
                            1.2f + (strain * 0.4f));
                }
            }

            // Server-side continuous particle calls REMOVED entirely! Client handles it now.
        }
    }

    private static boolean isBound(ServerPlayer caster) {
        return ACTIVE_BINDS.containsKey(caster.getUUID());
    }

    private static void startBind(ServerPlayer caster, LivingEntity target) {
        if (caster == null || target == null) return;
        BindInstance b = ACTIVE_BINDS.computeIfAbsent(caster.getUUID(), k -> new BindInstance());
        b.targetUuid = target.getUUID();
        b.ticksLeft = BIND_DURATION_TICKS;

        // Notify clients to start drawing the tether
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new BloodBindPayload(caster.getId(), target.getId(), true));
    }

    private static void breakBind(ServerPlayer caster) {
        BindInstance b = ACTIVE_BINDS.remove(caster.getUUID());
        if (b != null) {
            // Notify clients to snap the tether locally
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new BloodBindPayload(caster.getId(), -1, false));
        }
    }

    private void spawnBloodChain(ServerLevel world, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3 dir = delta.scale(1.0 / len);
        int steps = Mth.clamp((int)(len / 1.5), 6, 40); // Optimized step sizing slightly

        Vec3 p = start;
        for (int i = 0; i <= steps; i++) {
            double jx = (RNG.nextDouble() - 0.5) * 0.12;
            double jy = (RNG.nextDouble() - 0.5) * 0.12;
            double jz = (RNG.nextDouble() - 0.5) * 0.12;

            world.sendParticles(BLOOD_DUST, p.x + jx, p.y + jy, p.z + jz,
                    1, 0.02, 0.02, 0.02, 0.0);

            if (RNG.nextFloat() < 0.10f) {
                world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z,
                        1, 0.05, 0.05, 0.05, 0.0);
            }

            p = p.add(dir.scale(len / steps));
        }
    }

    private void spawnBindDamageFx(ServerLevel w, LivingEntity caster, LivingEntity target, float amount) {
        float intensity = Mth.clamp(amount / 10.0f, 0.15f, 1.0f);

        Vec3 a = caster.position().add(0, caster.getBbHeight() * 0.65, 0);
        Vec3 b = target.position().add(0, target.getBbHeight() * 0.65, 0);

        w.sendParticles(ParticleTypes.DAMAGE_INDICATOR, a.x, a.y, a.z, 3 + (int)(intensity * 4), 0.25, 0.30, 0.25, 0.02);
        w.sendParticles(ParticleTypes.DAMAGE_INDICATOR, b.x, b.y, b.z, 3 + (int)(intensity * 4), 0.25, 0.30, 0.25, 0.02);
        w.sendParticles(BLOOD_DUST, a.x, a.y, a.z, 4 + (int)(intensity * 6), 0.35, 0.40, 0.35, 0.0);
        w.sendParticles(BLOOD_DUST, b.x, b.y, b.z, 4 + (int)(intensity * 6), 0.35, 0.40, 0.35, 0.0);

        Vec3 delta = b.subtract(a);
        double len = delta.length();
        if (len > 0.01) {
            Vec3 dir = delta.scale(1.0 / len);
            int steps = Mth.clamp((int)(len / 1.2), 3, 14);
            Vec3 p = a;
            for (int i = 0; i <= steps; i++) {
                w.sendParticles(BLOOD_DUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                p = p.add(dir.scale(len / steps));
            }
        }

        w.playSound(null, caster.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, caster.getSoundSource(), 0.45f, 0.85f + (intensity * 0.3f));
    }

    // OTHER HELPERS

    public static boolean cleanseBleed(LivingEntity target) {
        if (ACTIVE_BLEEDS.remove(target.getUUID()) != null) {
            target.removeEffect(ModEffects.BLEED);
            return true;
        }
        return false;
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 9_000; }
    @Override public long getSecondaryCooldownMs() { return 13_500; }
    @Override public long getUltimateCooldownMs() { return 260_000; }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.blood.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.blood.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.blood.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.blood.ultimate_name").getString(); }
    @Override public String getPassiveName() { return Component.translatable("power.loopypowers.blood.passive_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.blood.description.overview").getString();
    }
    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.blood.description.passive").getString();
    }
    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.blood.description.primary").getString();
    }
    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.blood.description.secondary").getString();
    }
    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.blood.description.ultimate").getString();
    }
}