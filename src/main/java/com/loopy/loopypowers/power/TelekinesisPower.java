package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;

import com.loopy.loopypowers.network.payload.TelekinesisParticlePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Collections;

public class TelekinesisPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, TKCasterState> ACTIVE_CASTERS = new HashMap<>();
    private static final Map<UUID, TKVictimState> ACTIVE_VICTIMS = new HashMap<>();

    private static class DebrisField {
        final Map<UUID, Double> orbitAngles = new HashMap<>();
        final Queue<BlockPos> harvestQueue = new LinkedList<>();
        int ticksRemaining = 0;
        boolean wooliamSpawned = false;
    }

    private static class TKCasterState {
        int readyThrowTicks = 0;
        int throwCd = 0;
        int debrisThrowCd = 0;
        boolean prevSwing = false;
        long lastSwingTime = 0;

        DebrisField debrisField = null;
        final Map<UUID, Vec3> thrownBlocks = new HashMap<>();
    }

    private static class TKVictimState {
        int suspendTicks = 0;
        int chokeTicks = 0;
        UUID suspendOwner = null;

        int airborneTicks = 0;
        UUID impactOwner = null;
        Vec3 prevVelocity = null;

        int yankTicks = 0;
        UUID yankOwner = null;
    }

    private static TKCasterState getCasterState(Entity player) {
        return ACTIVE_CASTERS.computeIfAbsent(player.getUUID(), k -> new TKCasterState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // ── Passive — Forceful Strikes ──────────────────────────────
    private static final double PASSIVE_KB_MULT     = 1.8;
    private static final double PASSIVE_KB_VERTICAL = 0.2;

    // ── Primary — Yank ──────────────────────────────────────────
    private static final double YANK_RANGE             = 12.0;
    private static final double YANK_CONE_DOT          = 0.6;
    private static final double YANK_STRENGTH          = 0.9;
    private static final double YANK_VERTICAL          = 0.3;
    private static final float  YANK_EXTRA_FALL_DAMAGE = 6.0f;
    private static final int    YANK_FALL_WINDOW_TICKS = 60;

    // ── Secondary Suspend / throw phase ─────────────────────────────
    private static final int    SUSPEND_TICKS         = 50;
    private static final double SUSPEND_FLOAT_VEL     = 0.05;
    private static final int    THROW_READY_TICKS     = 45;
    private static final double THROW_SCAN_RANGE      = 14.0;
    private static final double THROW_SCAN_WIDTH      = 2.5;
    private static final double THROW_SPEED_H         = 1.5;
    private static final double THROW_SPEED_V         = 0.6;
    private static final double THROW_RELEASE_RANGE   = 12.0;

    // ── Choke phase ──────────────────────
    private static final int    CHOKE_TICKS           = 60;
    private static final int    CHOKE_DAMAGE_INTERVAL = 10;
    private static final float  CHOKE_DAMAGE_PER_TICK = 2.0f;
    private static final double CHOKE_ENTRY_LIFT       = 0.28;  // upward boost applied on the very first choke tick
    private static final double CHOKE_HOVER_VEL        = 0.06;  // per-tick y velocity to counteract gravity and hold position

    // EGG
    private static final int    QUOTE_CHANCE           = 450;

    // ── Impact system ──
    private static final int    TK_AIRBORNE_TICKS   = 40;
    private static final double IMPACT_MIN_SPEED_H  = 0.6;
    private static final double IMPACT_MIN_SPEED_V  = 0.7;
    private static final float  IMPACT_WALL_DAMAGE  = 16.0f;
    private static final float  IMPACT_WALL_SCALE   = 2.5f;
    private static final float  IMPACT_FLOOR_DAMAGE = 9.5f;
    private static final float  IMPACT_FLOOR_SCALE  = 1.8f;

    // ── Ultimate ──────────────────────────────────
    private static final int    DEBRIS_MAX_BLOCKS            = 8;
    private static final double DEBRIS_HARVEST_RADIUS        = 5.0;
    private static final int    DEBRIS_ORBIT_TICKS           = 260;
    private static final double DEBRIS_ORBIT_RADIUS          = 6.5;
    private static final double DEBRIS_ORBIT_RADIUS_INNER    = 2.5;
    private static final double DEBRIS_ORBIT_SPEED           = 0.035;
    private static final double DEBRIS_ORBIT_SPEED_INNER     = 0.20;
    private static final double DEBRIS_PULL_RADIUS           = 12.0;
    private static final double DEBRIS_PULL_STRENGTH         = 0.07;
    private static final float  DEBRIS_PULL_DAMAGE           = 0.5f;
    private static final float  DEBRIS_THROW_DAMAGE          = 13.0f;
    private static final double DEBRIS_THROW_SPEED           = 2.4;
    private static final double DEBRIS_THROW_EXPLOSION_RADIUS = 4.0;
    private static final double DEBRIS_INTERCEPT_RADIUS      = 5.0;

    // block regen
    private static final int    DEBRIS_REGEN_DELAY_TICKS = 15;
    private static final int    DEBRIS_REGEN_INTERVAL    = 10;
    private static final int    DEBRIS_REGEN_AMOUNT      = 4;

    // EGG
    private static final int    WOOLLIAM_CHANCE            = 70;

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    public static float onDamageGlobal(LivingEntity victim, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL)) {
            TKVictimState vState = ACTIVE_VICTIMS.get(victim.getUUID());
            if (vState != null && vState.yankTicks > 0) {
                vState.yankTicks = 0;
                vState.yankOwner = null;

                if (victim.level() instanceof ServerLevel w) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new TelekinesisParticlePayload(
                            TelekinesisParticlePayload.YANK_FALL_DAMAGE, 0, victim.getX(), victim.getY() + 0.5, victim.getZ(), 0, 0, 0, 0
                    ));
                    w.playSound(null, victim.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, victim.getSoundSource(), 1.0f, 0.8f);
                }

                return amount + YANK_EXTRA_FALL_DAMAGE;
            }
        }
        return amount;
    }

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("tk_"));
        ACTIVE_CASTERS.put(player.getUUID(), new TKCasterState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("tk_"));

        UUID pid = player.getUUID();
        TKCasterState caster = ACTIVE_CASTERS.remove(pid);

        if (caster != null && player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                if (caster.debrisField != null) {
                    for (UUID uuid : caster.debrisField.orbitAngles.keySet()) {
                        Entity e = w.getEntity(uuid);
                        if (e != null) e.discard();
                    }
                }

                for (UUID uuid : caster.thrownBlocks.keySet()) {
                    Entity e = w.getEntity(uuid);
                    if (e != null) e.discard();
                }

                w.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(150),
                                e -> e.getTags().contains("tk_debris") && e.getTags().contains(player.getStringUUID()))
                        .forEach(Entity::discard);
            }
            caster.debrisField = null;
            caster.thrownBlocks.clear();
        }

        Iterator<Map.Entry<UUID, TKVictimState>> it = ACTIVE_VICTIMS.entrySet().iterator();
        while (it.hasNext()) {
            TKVictimState vState = it.next().getValue();
            if (pid.equals(vState.suspendOwner)) {
                vState.suspendOwner = null;
                vState.suspendTicks = 0;
                vState.chokeTicks = 0;
            }
            if (pid.equals(vState.impactOwner)) {
                vState.impactOwner = null;
                vState.airborneTicks = 0;
                vState.prevVelocity = null;
            }
            if (pid.equals(vState.yankOwner)) {
                vState.yankOwner = null;
                vState.yankTicks = 0;
            }
            if (vState.suspendTicks <= 0 && vState.chokeTicks <= 0 && vState.airborneTicks <= 0 && vState.yankTicks <= 0) {
                it.remove();
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
        TKCasterState caster = getCasterState(player);

        boolean isSwinging = player.swinging;
        boolean wasSwinging = caster.prevSwing;

        if (isSwinging && !wasSwinging) {
            if (caster.throwCd <= 0) {
                performThrow(player, caster);
                caster.throwCd = 8;
            }
        }

        if (isSwinging) {
            throwDebrisProjectile(player, caster);
        }

        caster.prevSwing = isSwinging;
        if (caster.throwCd > 0) caster.throwCd--;
        if (caster.readyThrowTicks > 0) caster.readyThrowTicks--;
        if (caster.debrisThrowCd > 0) caster.debrisThrowCd--;

        Iterator<Map.Entry<UUID, TKVictimState>> it = ACTIVE_VICTIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TKVictimState> entry = it.next();
            TKVictimState vState = entry.getValue();

            boolean ownsSuspend = player.getUUID().equals(vState.suspendOwner);
            boolean ownsImpact = player.getUUID().equals(vState.impactOwner);
            boolean ownsYank = player.getUUID().equals(vState.yankOwner);

            if (!ownsSuspend && !ownsImpact && !ownsYank) continue;

            Entity ent = world.getEntity(entry.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                if (ownsSuspend) { vState.suspendTicks = 0; vState.chokeTicks = 0; vState.suspendOwner = null; }
                if (ownsImpact) { vState.airborneTicks = 0; vState.impactOwner = null; vState.prevVelocity = null; }
                if (ownsYank) { vState.yankTicks = 0; vState.yankOwner = null; }
            } else {
                if (ownsSuspend) handleSuspendAndChoke(le, world, vState);
                if (ownsImpact) handleImpactDamage(le, world, vState);
                if (ownsYank) {
                    if (vState.yankTicks > 0) {
                        vState.yankTicks--;
                        if (vState.yankTicks <= 0) vState.yankOwner = null;
                    }
                }
            }

            if (vState.suspendTicks <= 0 && vState.chokeTicks <= 0 && vState.airborneTicks <= 0 && vState.yankTicks <= 0) {
                it.remove();
            }
        }

        handleDebrisField(player, caster);
        tickThrownBlocks(player, caster);
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;

        TKCasterState caster = getCasterState(attacker);
        caster.lastSwingTime = attacker.serverLevel().getGameTime();

        Vec3 look = attacker.getViewVector(1.0f);
        target.setDeltaMovement(target.getDeltaMovement().add(look.x * PASSIVE_KB_MULT, PASSIVE_KB_VERTICAL, look.z * PASSIVE_KB_MULT));
        target.hasImpulse = true;
        target.hurtMarked = true; // sync velocity to all tracking clients
        if (target instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

        markForImpactTracking(target, target.getDeltaMovement(), attacker.getUUID());

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.PASSIVE_HIT, 0, target.getX(), target.getY(0.7), target.getZ(), 0, 0, 0, 0
        ));
    }

    private void markForImpactTracking(LivingEntity entity, Vec3 launchVelocity, UUID attackerUuid) {
        TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(entity.getUUID(), k -> new TKVictimState());
        vState.airborneTicks = TK_AIRBORNE_TICKS;
        vState.impactOwner = attackerUuid;
        vState.prevVelocity = launchVelocity;
    }

    private void handleImpactDamage(LivingEntity entity, ServerLevel world, TKVictimState vState) {
        if (vState.airborneTicks <= 0) return;

        vState.airborneTicks--;

        Vec3 prev = vState.prevVelocity;
        Vec3 curr = entity.getDeltaMovement();

        if (prev != null) {
            double prevH = Math.sqrt(prev.x * prev.x + prev.z * prev.z);
            double currH = Math.sqrt(curr.x * curr.x + curr.z * curr.z);

            boolean wallStopped = prevH > IMPACT_MIN_SPEED_H && currH < prevH * 0.35 && entity.horizontalCollision;
            boolean floorHit = prev.y < -IMPACT_MIN_SPEED_V && entity.onGround();

            Entity attacker = resolveOwner(vState.impactOwner, world);

            if (wallStopped) {
                float damage = IMPACT_WALL_DAMAGE + (float)(prevH - IMPACT_MIN_SPEED_H) * IMPACT_WALL_SCALE;
                entity.hurt(ModDamageTypes.wallCollision(world, attacker), damage);

                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TelekinesisParticlePayload(
                        TelekinesisParticlePayload.WALL_IMPACT, 0, entity.getX(), entity.getY() + 0.8, entity.getZ(), 0, 0, 0, 0
                ));
                world.playSound(null, entity.blockPosition(), SoundEvents.STONE_HIT, entity.getSoundSource(), 0.9f, 0.7f);

                vState.airborneTicks = 0;
                vState.prevVelocity = null;
                vState.impactOwner = null;
                return;
            }

            if (floorHit) {
                float damage = IMPACT_FLOOR_DAMAGE + (float)(-prev.y - IMPACT_MIN_SPEED_V) * IMPACT_FLOOR_SCALE;
                entity.hurt(ModDamageTypes.wallCollision(world, attacker), damage);

                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TelekinesisParticlePayload(
                        TelekinesisParticlePayload.FLOOR_IMPACT, 0, entity.getX(), entity.getY(), entity.getZ(), 0, 0, 0, 0
                ));
                world.playSound(null, entity.blockPosition(), SoundEvents.STONE_FALL, entity.getSoundSource(), 0.8f, 0.9f);

                vState.airborneTicks = 0;
                vState.prevVelocity = null;
                vState.impactOwner = null;
                return;
            }
        }

        vState.prevVelocity = curr;

        if (vState.airborneTicks <= 0) {
            vState.impactOwner = null;
            vState.prevVelocity = null;
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 end    = origin.add(look.scale(YANK_RANGE));

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.YANK_CAST, 0, origin.x, origin.y, origin.z, end.x, end.y, end.z, 0
        ));

        boolean hitAnything = false;
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(YANK_RANGE),
                en -> en.isAlive() && en != player)) {

            Vec3 toTarget = e.position().subtract(origin).normalize();
            if (look.dot(toTarget) < YANK_CONE_DOT) continue;

            Vec3 pull = origin.subtract(e.position()).normalize().scale(YANK_STRENGTH);
            Vec3 launchVel = e.getDeltaMovement().add(pull.x, pull.y + YANK_VERTICAL, pull.z);
            e.setDeltaMovement(launchVel);
            e.hasImpulse = true;
            e.hurtMarked = true; // sync yank velocity to all tracking clients
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

            markForImpactTracking(e, launchVel, player.getUUID());

            TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(e.getUUID(), k -> new TKVictimState());
            vState.yankTicks = YANK_FALL_WINDOW_TICKS;
            vState.yankOwner = player.getUUID();

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.IMPACT_RING, 0, e.getX(), e.getY(), e.getZ(), 8, 0.25, 0, 0
            ));
            hitAnything = true;
        }

        world.playSound(null, player.blockPosition(),
                ModSounds.YANK.get(), player.getSoundSource(), 0.6f, 1.2f);
        if (hitAnything) world.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL, player.getSoundSource(), 0.4f, 1.5f);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1.0f);
        Vec3 end    = origin.add(look.scale(THROW_SCAN_RANGE));

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.SUSPEND_CAST, 0, origin.x, origin.y, origin.z, end.x, end.y, end.z, 0
        ));

        int grabbed = 0;
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, end).inflate(THROW_SCAN_WIDTH),
                en -> en.isAlive() && en != player)) {

            TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(e.getUUID(), k -> new TKVictimState());
            vState.suspendTicks = SUSPEND_TICKS;
            vState.chokeTicks = 0;
            vState.suspendOwner = player.getUUID();

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.IMPACT_RING, 0, e.getX(), e.getY(), e.getZ(), 10, 0.3, 0, 0
            ));
            grabbed++;
        }

        if (grabbed > 0) {
            TKCasterState caster = getCasterState(player);
            caster.readyThrowTicks = THROW_READY_TICKS;
        }

        world.playSound(null, player.blockPosition(),
                ModSounds.SUSPEND.get(), player.getSoundSource(), 0.7f, 0.9f);
    }

    private void handleSuspendAndChoke(LivingEntity e, ServerLevel world, TKVictimState vState) {
        if (vState.suspendTicks > 0) {
            vState.suspendTicks--;
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, SUSPEND_FLOAT_VEL, e.getDeltaMovement().z * 0.3);
            e.hasImpulse = true;
            e.hurtMarked = true; // sync per-tick suspend float to all tracking clients
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 4, true, false, false));

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.SUSPEND_TICK, e.getId(), 0, 0, 0, 0, 0, 0, 0
            ));

            if (vState.suspendTicks <= 0) {
                vState.chokeTicks = CHOKE_TICKS;

                if (e instanceof ServerPlayer && world.random.nextInt(QUOTE_CHANCE) == 0) {
                    Entity owner = resolveOwner(vState.suspendOwner, world);
                    if (owner instanceof ServerPlayer attacker) {
                        Component message = Component.literal(
                                "<" + attacker.getName().getString() + "> I find your lack of faith... disturbing..."
                        );
                        world.getServer().getPlayerList().broadcastSystemMessage(message, false);
                    }
                }
            }
            return;
        }

        if (vState.chokeTicks > 0) {
            boolean chokeEntry = (vState.chokeTicks == CHOKE_TICKS);
            vState.chokeTicks--;
            float chokeProgress = 1.0f - ((float) vState.chokeTicks / CHOKE_TICKS);
            double newY = chokeEntry ? CHOKE_ENTRY_LIFT : CHOKE_HOVER_VEL;
            e.setDeltaMovement(e.getDeltaMovement().x * 0.2, newY, e.getDeltaMovement().z * 0.2);
            e.hasImpulse = true;
            e.hurtMarked = true; // sync per-tick choke hover to all tracking clients
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

            if (e.tickCount % CHOKE_DAMAGE_INTERVAL == 0) {
                Entity attacker = resolveOwner(vState.suspendOwner, world);
                e.hurt(ModDamageTypes.strangle(world, attacker), CHOKE_DAMAGE_PER_TICK * (0.5f + chokeProgress));
                world.playSound(null, e.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, e.getSoundSource(), 0.4f + chokeProgress * 0.3f, 0.9f);
            }

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.CHOKE_TICK, e.getId(), 0, 0, 0, 0, 0, 0, chokeProgress
            ));

            if (vState.chokeTicks <= 0) {
                vState.suspendOwner = null;
            }
        }
    }

    private void performThrow(ServerPlayer player, TKCasterState caster) {
        if (caster.readyThrowTicks <= 0) return;
        caster.readyThrowTicks = 0;

        Vec3 look = player.getViewVector(1.0f);
        ServerLevel world = player.serverLevel();

        Vec3 throwVel = new Vec3(
                look.x * THROW_SPEED_H,
                THROW_SPEED_V,
                look.z * THROW_SPEED_H
        );

        boolean thrown = false;

        for (Map.Entry<UUID, TKVictimState> entry : ACTIVE_VICTIMS.entrySet()) {
            TKVictimState vState = entry.getValue();
            if ((vState.suspendTicks > 0 || vState.chokeTicks > 0) && player.getUUID().equals(vState.suspendOwner)) {
                Entity e = world.getEntity(entry.getKey());
                if (e instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(player) <= (THROW_RELEASE_RANGE * THROW_RELEASE_RANGE)) {

                    vState.suspendTicks = 0;
                    vState.chokeTicks = 0;
                    vState.suspendOwner = null;

                    le.setDeltaMovement(throwVel);
                    le.hasImpulse = true;
                    le.hurtMarked = true; // sync throw velocity to all tracking clients
                    if (le instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

                    markForImpactTracking(le, throwVel, player.getUUID());

                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(le, new TelekinesisParticlePayload(
                            TelekinesisParticlePayload.THROW_RELEASE, 0, le.getX(), le.getY(), le.getZ(), 0, 0, 0, 0
                    ));
                    thrown = true;
                }
            }
        }

        if (thrown) {
            world.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_FLAP,
                    player.getSoundSource(), 0.6f, 1.4f);
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        TKCasterState caster = getCasterState(player);

        if (caster.debrisField != null) {
            for (UUID uuid : caster.debrisField.orbitAngles.keySet()) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }

        world.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(150),
                        e -> e.getTags().contains("tk_debris") && e.getTags().contains(player.getStringUUID()))
                .forEach(Entity::discard);

        DebrisField field = new DebrisField();
        field.ticksRemaining = DEBRIS_ORBIT_TICKS;
        caster.debrisField = field;

        queueHarvestBlocks(player, world, field);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.ULTIMATE_CAST, 0, player.getX(), player.getY(0.5), player.getZ(), 0, 0, 0, 0
        ));

        world.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, player.getSoundSource(), 1.2f, 0.4f);
    }

    private void queueHarvestBlocks(ServerPlayer player, ServerLevel world, DebrisField field) {
        Vec3 center = player.position();
        int radius = (int) Math.ceil(DEBRIS_HARVEST_RADIUS);
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = player.blockPosition().offset(dx, dy, dz);
                    if (pos.closerToCenterThan(center, DEBRIS_HARVEST_RADIUS) && isHarvestable(world, pos)) {
                        candidates.add(pos);
                    }
                }
            }
        }

        Collections.shuffle(candidates);

        int count = 0;
        for (BlockPos pos : candidates) {
            if (count >= DEBRIS_MAX_BLOCKS) break;
            field.harvestQueue.add(pos);
            count++;
        }
    }

    private void harvestSingleBlock(ServerPlayer player, ServerLevel world, DebrisField field, BlockPos pos) {
        while (!isHarvestable(world, pos) && !field.harvestQueue.isEmpty()) {
            pos = field.harvestQueue.poll();
        }
        if (!isHarvestable(world, pos)) return;

        BlockState state = world.getBlockState(pos);

        Entity orbitEntity;

        if (!field.wooliamSpawned && world.random.nextInt(WOOLLIAM_CHANCE) == 0) {
            Sheep sheep = EntityType.SHEEP.create(world);
            if (sheep != null) {
                sheep.setCustomName(Component.literal("Woolliam"));
                sheep.setNoGravity(true);
                sheep.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                world.removeBlock(pos, false);
                world.addFreshEntity(sheep);
                orbitEntity = sheep;
                field.wooliamSpawned = true;
            } else {
                world.removeBlock(pos, false);
                orbitEntity = FallingBlockEntity.fall(world, pos, state);
            }
        } else {
            world.removeBlock(pos, false);
            orbitEntity = FallingBlockEntity.fall(world, pos, state);
        }

        if (orbitEntity instanceof FallingBlockEntity falling) {
            falling.setNoGravity(true);
            falling.dropItem = false;
            falling.time = -32768;
        }

        orbitEntity.addTag("tk_debris");
        orbitEntity.addTag(player.getStringUUID());

        field.orbitAngles.put(orbitEntity.getUUID(), world.random.nextDouble() * Math.PI * 2.0);

        world.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.DEBRIS_HARVEST, 0, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0, 0
        ));
    }

    private boolean isHarvestable(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || world.getBlockEntity(pos) != null) return false;
        if (!state.canOcclude()) return false;

        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0 || hardness > 4.0f) return false;

        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.SNOW)
                || state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_HOE)
                || state.is(BlockTags.LEAVES);
    }

    private void handleDebrisField(ServerPlayer player, TKCasterState caster) {
        DebrisField field = caster.debrisField;
        if (field == null || field.ticksRemaining <= 0) return;

        ServerLevel world = player.serverLevel();

        if (!field.harvestQueue.isEmpty()) {
            BlockPos pos = field.harvestQueue.poll();
            harvestSingleBlock(player, world, field, pos);
        }

        handleDebrisRegen(player, caster, field);
        field.ticksRemaining--;

        if (field.ticksRemaining <= 0) {
            for (UUID uuid : field.orbitAngles.keySet()) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
            caster.debrisField = null;
            return;
        }

        Vec3 playerPos = player.position().add(0, 1.0, 0);
        Set<UUID> toRemove = new HashSet<>();
        int total = field.orbitAngles.size();

        int index = 0;
        for (Map.Entry<UUID, Double> entry : field.orbitAngles.entrySet()) {
            UUID uuid = entry.getKey();
            Entity ent = world.getEntity(uuid);

            if (ent == null || ent.isRemoved()) {
                toRemove.add(uuid);
                index++;
                continue;
            }

            boolean isInner = (index % 2 == 1);
            double orbitRadius = isInner ? DEBRIS_ORBIT_RADIUS_INNER : DEBRIS_ORBIT_RADIUS;
            double orbitSpeed  = isInner ? -DEBRIS_ORBIT_SPEED_INNER : DEBRIS_ORBIT_SPEED;

            double angle = (Math.PI * 2.0 * (double)(index / 2)) / (double) Math.max(1, total / 2) + (world.getGameTime() * orbitSpeed);
            entry.setValue(angle);

            double x = playerPos.x + Math.cos(angle) * orbitRadius;
            double z = playerPos.z + Math.sin(angle) * orbitRadius;
            double y = playerPos.y + Math.sin(angle * 1.5 + world.getGameTime() * 0.08) * 0.5 + (isInner ? 0.3 : 0.0);

            Vec3 target = new Vec3(x, y, z);
            ent.setDeltaMovement(target.subtract(ent.position()).scale(0.35));
            ent.hasImpulse = true;
            ent.hurtMarked = true; // sync per-tick orbit velocity to tracking clients

            if (target.distanceTo(ent.position()) > 5.0) {
                ent.setPos(target.x, target.y, target.z);
                // broadcast position snap so clients don't see blocks rubber-banding
                world.getChunkSource().broadcastAndSend(ent, new ClientboundTeleportEntityPacket(ent));
            }

            ent.setNoGravity(true);
            if (ent instanceof FallingBlockEntity fb) fb.time = -32768;

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.ORBIT_TICK, 0, target.x, target.y, target.z, 0, 0, 0, isInner ? 1f : 0f
            ));
            index++;
        }
        toRemove.forEach(field.orbitAngles::remove);

        double contactRadius = DEBRIS_ORBIT_RADIUS_INNER * 1.2;

        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DEBRIS_PULL_RADIUS),
                en -> en.isAlive() && en != player)) {

            Vec3 toPlayer = playerPos.subtract(e.position());
            double dist = toPlayer.length();
            if (dist < 0.5 || dist > DEBRIS_PULL_RADIUS) continue;

            double strength = DEBRIS_PULL_STRENGTH * (1.0 - dist / DEBRIS_PULL_RADIUS);
            Vec3 pull = toPlayer.normalize().scale(strength);

            e.setDeltaMovement(e.getDeltaMovement().add(pull.x, pull.y * 0.3, pull.z));
            e.hasImpulse = true;
            e.hurtMarked = true; // sync per-tick pull velocity to all tracking clients
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

            if (dist <= contactRadius && world.getGameTime() % 10 == 0) {
                e.hurt(ModDamageTypes.debrisOrbit(world, player), DEBRIS_PULL_DAMAGE);
            }

            if (world.getGameTime() % 4 == 0) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new TelekinesisParticlePayload(
                        TelekinesisParticlePayload.PULL_TICK, 0, e.getX(), e.getY(0.5), e.getZ(), pull.x * 2, pull.y * 2, pull.z * 2, 0
                ));
            }
        }

        interceptProjectiles(player, world, field);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.DEBRIS_TICK, player.getId(), playerPos.x, playerPos.y, playerPos.z, 0, 0, 0, 0
        ));
    }

    private void interceptProjectiles(ServerPlayer player, ServerLevel world, DebrisField field) {
        List<Projectile> projectiles = world.getEntitiesOfClass(
                Projectile.class,
                player.getBoundingBox().inflate(DEBRIS_INTERCEPT_RADIUS),
                p -> p.getOwner() != player && p.isAlive()
        );

        for (Projectile p : projectiles) {
            if (field.orbitAngles.isEmpty()) break;

            UUID sacrificeId = field.orbitAngles.keySet().iterator().next();
            field.orbitAngles.remove(sacrificeId);
            Entity sacrifice = world.getEntity(sacrificeId);

            if (sacrifice != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                        TelekinesisParticlePayload.SLAM_IMPACT, 0, sacrifice.getX(), sacrifice.getY(), sacrifice.getZ(), 0, 0, 0, 0
                ));
                sacrifice.discard();
            } else {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                        TelekinesisParticlePayload.SLAM_IMPACT, 0, p.getX(), p.getY(), p.getZ(), 0, 0, 0, 0
                ));
            }

            p.discard();
            world.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
            world.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
        }
    }

    private void throwDebrisProjectile(ServerPlayer player, TKCasterState caster) {
        DebrisField field = caster.debrisField;
        if (field == null || field.orbitAngles.isEmpty()) return;
        if (caster.debrisThrowCd > 0) return;

        ServerLevel world = player.serverLevel();

        Iterator<Map.Entry<UUID, Double>> it = field.orbitAngles.entrySet().iterator();
        if (!it.hasNext()) return;

        Map.Entry<UUID, Double> entry = it.next();
        UUID uuid = entry.getKey();
        Entity orbitEntity = world.getEntity(uuid);
        it.remove();

        if (orbitEntity != null && !orbitEntity.isRemoved()) {
            orbitEntity.setNoGravity(true);

            if (orbitEntity instanceof FallingBlockEntity fb) {
                fb.time = -32768;
                fb.dropItem = false;
            }

            Vec3 eyePos = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0f);
            Vec3 targetPoint = eyePos.add(look.scale(60.0));

            var hit = world.clip(new ClipContext(eyePos, targetPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                targetPoint = hit.getLocation();
            }

            Vec3 vel = targetPoint.subtract(orbitEntity.position()).normalize().scale(DEBRIS_THROW_SPEED);
            orbitEntity.setDeltaMovement(vel);
            orbitEntity.hasImpulse = true;

            caster.thrownBlocks.put(uuid, orbitEntity.position());

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.DEBRIS_THROW, 0, orbitEntity.getX(), orbitEntity.getY(), orbitEntity.getZ(), 0, 0, 0, 0
            ));

            world.playSound(null, player.blockPosition(), SoundEvents.EGG_THROW, player.getSoundSource(), 1.0f, 0.6f);
            world.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, player.getSoundSource(), 0.8f, 1.8f);

            caster.debrisThrowCd = 3;
        }
    }

    private void tickThrownBlocks(ServerPlayer player, TKCasterState caster) {
        if (caster.thrownBlocks.isEmpty()) return;

        ServerLevel world = player.serverLevel();
        Set<UUID> toExplode = new HashSet<>();

        for (Map.Entry<UUID, Vec3> entry : caster.thrownBlocks.entrySet()) {
            UUID entityUuid = entry.getKey();
            Entity ent = world.getEntity(entityUuid);

            if (ent == null || ent.isRemoved()) {
                triggerDebrisExplosion(player, world, entry.getValue());
                toExplode.add(entityUuid);
                continue;
            }

            entry.setValue(ent.position());

            Vec3 vel = ent.getDeltaMovement();
            boolean hitSomething = ent.horizontalCollision || ent.onGround() || vel.lengthSqr() < 0.2;

            if (hitSomething) {
                triggerDebrisExplosion(player, world, ent.position());
                ent.discard();
                toExplode.add(entityUuid);
            }
        }

        toExplode.forEach(caster.thrownBlocks::remove);
    }

    private void triggerDebrisExplosion(ServerPlayer player, ServerLevel world, Vec3 pos) {
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos, pos).inflate(DEBRIS_THROW_EXPLOSION_RADIUS),
                en -> en.isAlive() && en != player)) {

            double dist = e.position().distanceTo(pos);
            if (dist > DEBRIS_THROW_EXPLOSION_RADIUS) continue;

            float damage = DEBRIS_THROW_DAMAGE * (float) (1.0 - dist / DEBRIS_THROW_EXPLOSION_RADIUS);
            e.hurt(ModDamageTypes.blockThrow(world, player), damage);

            Vec3 knockback = e.position().subtract(pos).normalize().scale(0.8);
            e.setDeltaMovement(e.getDeltaMovement().add(knockback.x, 0.4, knockback.z));
            e.hasImpulse = true;
            e.hurtMarked = true; // sync explosion knockback to all tracking clients
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

            markForImpactTracking(e, e.getDeltaMovement(), player.getUUID());
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                TelekinesisParticlePayload.DEBRIS_EXPLOSION, 0, pos.x, pos.y, pos.z, 0, 0, 0, 0
        ));

        world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), player.getSoundSource(), 0.6f, 0.5f);
    }

    private void handleDebrisRegen(ServerPlayer player, TKCasterState caster, DebrisField field) {
        ServerLevel world = player.serverLevel();
        long time = world.getGameTime();

        long idleTime = time - caster.lastSwingTime;

        if (!field.harvestQueue.isEmpty()) return;

        if (idleTime < DEBRIS_REGEN_DELAY_TICKS) return;
        if (time % DEBRIS_REGEN_INTERVAL != 0) return;
        if (field.orbitAngles.size() >= DEBRIS_MAX_BLOCKS) return;

        int toRegen = Math.min(DEBRIS_REGEN_AMOUNT, DEBRIS_MAX_BLOCKS - field.orbitAngles.size());

        for (int i = 0; i < toRegen; i++) {
            spawnOrbitBlock(player, world, field);
        }
    }

    private void spawnOrbitBlock(ServerPlayer player, ServerLevel world, DebrisField field) {
        BlockPos targetPos = null;
        BlockPos center = player.blockPosition();
        int radius = 8;
        for (int i = 0; i < 30; i++) {
            BlockPos p = center.offset(
                    world.random.nextInt(radius * 2) - radius,
                    world.random.nextInt(8) - 4,
                    world.random.nextInt(radius * 2) - radius
            );
            if (isHarvestable(world, p)) {
                targetPos = p;
                break;
            }
        }

        BlockState state;
        double px, py, pz;

        if (targetPos != null) {
            state = world.getBlockState(targetPos);
            px = targetPos.getX() + 0.5;
            py = targetPos.getY();
            pz = targetPos.getZ() + 0.5;
            world.playSound(null, targetPos, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TelekinesisParticlePayload(
                    TelekinesisParticlePayload.DEBRIS_HARVEST, 0, px, py + 0.5, pz, 0, 0, 0, 0
            ));

            FallingBlockEntity block = FallingBlockEntity.fall(world, targetPos, state);
            block.setNoGravity(true);
            block.dropItem = false;
            block.time = -32768;
            block.addTag("tk_debris");
            block.addTag(player.getStringUUID());
            field.orbitAngles.put(block.getUUID(), world.random.nextDouble() * Math.PI * 2);
        } else {
            state = Blocks.STONE.defaultBlockState();
            px = player.getX();
            py = player.getY() + 2.0;
            pz = player.getZ();

            BlockPos safePos = player.blockPosition().above(15);
            BlockState original = world.getBlockState(safePos);

            FallingBlockEntity block = FallingBlockEntity.fall(world, safePos, state);
            world.setBlock(safePos, original, 3);

            block.setPos(px, py, pz);
            block.setNoGravity(true);
            block.dropItem = false;
            block.time = -32768;
            block.addTag("tk_debris");
            block.addTag(player.getStringUUID());

            field.orbitAngles.put(block.getUUID(), world.random.nextDouble() * Math.PI * 2);
        }
    }

    private static Entity resolveOwner(UUID ownerUuid, ServerLevel world) {
        if (ownerUuid == null) return null;
        return world.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    public static boolean cleanseTelekinesis(LivingEntity target) {
        TKVictimState vState = ACTIVE_VICTIMS.get(target.getUUID());
        if (vState != null && (vState.suspendTicks > 0 || vState.chokeTicks > 0 || vState.yankTicks > 0)) {
            vState.suspendTicks = 0;
            vState.chokeTicks = 0;
            vState.suspendOwner = null;

            vState.yankTicks = 0;
            vState.yankOwner = null;

            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            return true;
        }
        return false;
    }

    /* ============================================================
       YAP
       ============================================================ */

    @Override public String getName()          { return Component.translatable("power.loopypowers.telekinesis.name").getString(); }
    @Override public String getPassiveName()   { return Component.translatable("power.loopypowers.telekinesis.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Component.translatable("power.loopypowers.telekinesis.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.telekinesis.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Component.translatable("power.loopypowers.telekinesis.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 11_000; }
    @Override public long getSecondaryCooldownMs() { return 37_000; }
    @Override public long getUltimateCooldownMs()  { return 310_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.telekinesis.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.telekinesis.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.telekinesis.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.telekinesis.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.telekinesis.description.ultimate").getString();
    }
}