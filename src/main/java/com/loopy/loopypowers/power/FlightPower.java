package com.loopy.loopypowers.power;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class FlightPower implements PowerInterface {
    private static final Random RNG = new Random();

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, FlightState> ACTIVE_STATES = new HashMap<>();

    // Tiny memory cache to bridge the easter egg tag across death/respawn
    private static final Set<UUID> DIED_WITH_EGG = new HashSet<>();

    private static class FlightState {
        boolean flightActive = false;
        boolean glideRequest = false;
        boolean boomInvuln = false;

        int stallTicks = 0;
        int trailStep = 0;
        int soundStep = 0;
        int wingEnforceStep = 0;
        int gustEmpowermentTicks = 0;
        int boomWindup = 0;
        int boomDash = 0;
        float boomYaw = 0;

        // easter egg timer
        int funnyTimer = 600;
    }

    private static FlightState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new FlightState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // FLIGHT STOP WHEN HURT
    private static final int HURT_LOCK_DURATION = 20 * 2; // ground after hit
    private static final float HURT_KNOCKOUT_MIN_YVEL = -1.15f; // y level drop when hurt

    // Trail + sound cadence
    private static final int TRAIL_INTERVAL = 2;   // particle trail delay in flight
    private static final int SOUND_INTERVAL = 10;  // sound loop delay

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        // Clean legacy tags, but keep the easter egg memory if they have it
        player.getTags().removeIf(tag -> tag.startsWith("fl_") && !tag.equals("fl_hecanfly_done"));

        ACTIVE_STATES.put(player.getUUID(), new FlightState());
        equipWings(player);

        // If they died with the easter egg, inject it into their new respawned body
        if (DIED_WITH_EGG.remove(player.getUUID())) {
            player.getTags().add("fl_hecanfly_done");
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        unequipWings(player);
        ACTIVE_STATES.remove(player.getUUID());

        player.removeEffect(ModEffects.GROUNDED);

        if (player.isAlive()) {
            player.getTags().remove("fl_hecanfly_done");
        }
    }

    @Override
    public void onDeath(ServerPlayer player) {
        // If they die with the easter egg completed, save it to the cache
        if (player.getTags().contains("fl_hecanfly_done")) {
            DIED_WITH_EGG.add(player.getUUID());
        }
        onRemove(player);
    }

    private static void equipWings(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

        if (chest.is(com.loopy.loopypowers.item.ModItems.WINGS_OF_VALOR.get())) return;

        if (!chest.isEmpty()) {
            boolean inserted = player.getInventory().add(chest.copy());
            if (!inserted) player.drop(chest.copy(), true);
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }

        ItemStack wings = new ItemStack(com.loopy.loopypowers.item.ModItems.WINGS_OF_VALOR.get());
        player.setItemSlot(EquipmentSlot.CHEST, wings);
    }

    private static void unequipWings(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(com.loopy.loopypowers.item.ModItems.WINGS_OF_VALOR.get())) return;

        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        FlightState state = getState(player);

        // EGG
        if (state.funnyTimer > 0) {
            state.funnyTimer--;
        } else {
            // lock egg (Set.add handles redundancy gracefully, removing the unnecessary contains check)
            player.getTags().add("fl_hecanfly_done");
        }

        // timers
        if (state.stallTicks > 0) state.stallTicks--;

        // enforce wings
        state.wingEnforceStep--;
        if (state.wingEnforceStep <= 0) {
            state.wingEnforceStep = 20;
            equipWings(player);
        }

        tickSonicBoomUltimate(player, state);

        // while flying tick
        tickPassiveFlight(player, state);

        // speed boost after dash
        tickGustEmpowerment(player, state);

        // particles while flying
        if (player.isFallFlying()) {
            tickFlightFx(player, state);
        } else {
            state.trailStep = 0;
            state.soundStep = 0;
        }
    }

    @Override
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        FlightState state = getState(victim);

        if (state.boomInvuln) return false; // Invulnerable during boom dash

        // now only ground when hit midair
        if (victim.isFallFlying() || state.flightActive) {
            // Force them downward + lock re-glide
            Vec3 v = victim.getDeltaMovement();
            victim.setDeltaMovement(v.x, Math.min(v.y, HURT_KNOCKOUT_MIN_YVEL), v.z);
            victim.hasImpulse = true;

            // FIX: Sync knock-out velocity
            victim.hurtMarked = true;
            victim.connection.send(new ClientboundSetEntityMotionPacket(victim));

            // Apply visual Grounded effect using registry
            victim.addEffect(new MobEffectInstance(ModEffects.GROUNDED, HURT_LOCK_DURATION, 0, false, false, true));

            // Clear flight states
            state.flightActive = false;
            state.trailStep = 0;
            state.soundStep = 0;
            victim.stopFallFlying();

            // particles
            ServerLevel w = victim.serverLevel();
            w.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    8, 0.35, 0.35, 0.35, 0.02);
            w.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PHANTOM_FLAP,
                    victim.getSoundSource(), 0.7f, 0.9f);
        }

        return true;
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    // PRIMARY
    // Gust
    private static final int GUST_EMPOWERMENT_DURATION = 40;
    private static final double GUST_BURST_STRENGTH = 1.0;
    private static final double GUST_EMPOWERMENT_PUSH = 0.06;
    private static final double GUST_MAX_HORIZ_SPEED = 2.2;

    @Override
    public boolean tryActivatePrimary(ServerPlayer player) {
        if (player.hasEffect(ModEffects.GROUNDED)) {
            player.displayClientMessage(Component.translatable("power.loopypowers.flight.grounded"), true);
            return false;
        }
        activatePrimary(player);
        return true;
    }

    @Override
    public void activatePrimary(ServerPlayer player) {
        // force flight to start
        getState(player).glideRequest = true;

        // FUNNY EGG
        if (player.isFallFlying() && !player.getTags().contains("fl_hecanfly_done")) {
            // % chance to play when used
            if (RNG.nextFloat() < 0.25f) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HECANFLY.get(), player.getSoundSource(), 1.2f, 1.0f);
                // mark em
                player.getTags().add("fl_hecanfly_done");
            }
        }

        // direction
        Vec3 look = player.getViewVector(1.0f);
        Vec3 dir = new Vec3(look.x, 0.0, look.z);

        if (dir.lengthSqr() < 1.0e-6) return;
        dir = dir.normalize();

        // apply boost
        Vec3 v = player.getDeltaMovement();
        Vec3 boosted = v.add(dir.scale(GUST_BURST_STRENGTH));

        Vec3 horiz = new Vec3(boosted.x, 0, boosted.z);
        double hLen = horiz.length();
        if (hLen > GUST_MAX_HORIZ_SPEED) {
            Vec3 clampedHoriz = horiz.normalize().scale(GUST_MAX_HORIZ_SPEED);
            boosted = new Vec3(clampedHoriz.x, boosted.y, clampedHoriz.z);
        }

        player.setDeltaMovement(boosted);
        player.hasImpulse = true;

        // FIX: Sync dash velocity
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        getState(player).gustEmpowermentTicks = GUST_EMPOWERMENT_DURATION;

        ServerLevel w = player.serverLevel();
        w.sendParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.8, player.getZ(),
                12, 0.25, 0.15, 0.25, 0.02
        );
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GUST.get(),
                player.getSoundSource(),
                0.9f, 1.6f
        );
    }

    private void tickGustEmpowerment(ServerPlayer player, FlightState state) {
        if (state.gustEmpowermentTicks <= 0) return;
        state.gustEmpowermentTicks--;

        if (!player.isFallFlying()) return;

        Vec3 look = player.getViewVector(1.0f);
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        if (dir.lengthSqr() < 1.0e-6) return;
        dir = dir.normalize();

        Vec3 v = player.getDeltaMovement();
        Vec3 next = v.add(dir.scale(GUST_EMPOWERMENT_PUSH));

        Vec3 horiz = new Vec3(next.x, 0, next.z);
        double hLen = horiz.length();
        if (hLen > GUST_MAX_HORIZ_SPEED) {
            Vec3 clampedHoriz = horiz.normalize().scale(GUST_MAX_HORIZ_SPEED);
            next = new Vec3(clampedHoriz.x, next.y, clampedHoriz.z);
        }

        player.setDeltaMovement(next);
        player.hasImpulse = true;

        // FIX: Sync dash empowerment velocity
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        if (state.gustEmpowermentTicks % 3 == 0) {
            player.serverLevel().sendParticles(
                    ParticleTypes.FIREWORK,
                    player.getX(), player.getY() + 0.6, player.getZ(),
                    2, 0.10, 0.06, 0.10, 0.005
            );
        }
    }

    // SECONDARY
    @Override
    public boolean tryActivateSecondary(ServerPlayer player) {
        if (player.hasEffect(ModEffects.GROUNDED)) {
            player.displayClientMessage(Component.translatable("power.loopypowers.flight.grounded"), true);
            return false;
        }
        activateSecondary(player);
        return true;
    }

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        w.sendParticles(
                ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.3, player.getZ(),
                20, 0.35, 0.25, 0.35, 0.03
        );
        w.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.UPDRAFT.get(),
                player.getSoundSource(),
                1.1f,
                1.3f
        );

        Vec3 v = player.getDeltaMovement();
        double up = 2.5;
        player.setDeltaMovement(v.x, Math.max(v.y, 0.0) + up, v.z);
        player.hasImpulse = true;

        // FIX: Sync updraft velocity
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        getState(player).glideRequest = true;
    }

    // Ultimate

    private static final int BOOM_WINDUP_TICKS = 30;
    private static final int BOOM_DASH_TICKS = 18;
    private static final double BOOM_SPEED = 4.0;
    private static final double BOOM_RADIUS = 4.0;
    private static final double BOOM_IMPACT_RADIUS = 6.0;
    private static final float  BOOM_IMPACT_DAMAGE = 15.0f;
    private static final double BOOM_IMPACT_KB = 2.8;
    private static final int BOOM_KNOCKOUT_DURATION = 20 * 3;

    @Override
    public boolean tryActivateUltimate(ServerPlayer player) {
        if (player.onGround() || player.isInWater()) {
            player.displayClientMessage(Component.translatable("power.loopypowers.flight.must_be_airborne"), true);
            return false;
        }
        activateUltimate(player);
        return true;
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        if (player.onGround() || player.isInWater()) return;

        FlightState state = getState(player);

        state.boomWindup = BOOM_WINDUP_TICKS;
        state.boomDash = 0;
        state.boomInvuln = false;

        player.setDeltaMovement(0, 0, 0);
        player.hasImpulse = true;

        // FIX: Sync mid-air freeze
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        player.fallDistance = 0;

        ServerLevel w = player.serverLevel();
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_CHARGE,
                player.getSoundSource(), 1.5f, 1.0f);

        w.sendParticles(ParticleTypes.ENCHANTED_HIT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                1, 0, 0, 0, 0);
    }

    private void tickSonicBoomUltimate(ServerPlayer player, FlightState state) {
        if (player.isCreative() || player.isSpectator()) return;

        ServerLevel world = player.serverLevel();

        // windup
        if (state.boomWindup > 0) {
            state.boomWindup--;

            CameraShake.shakeNearby(player, 4, 10, 0.40f);

            player.setDeltaMovement(0, 0, 0);
            player.hasImpulse = true;

            // FIX: Keep player perfectly frozen during charge
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));

            player.fallDistance = 0;

            if (state.boomWindup % 2 == 0) {
                world.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        6, 0.35, 0.45, 0.35, 0.01);
                world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        8, 0.45, 0.55, 0.45, 0.02);
            }

            if (state.boomWindup <= 0) {
                Vec3 dir = player.getViewVector(1.0f).normalize();
                state.boomYaw = player.getYRot();

                state.boomDash = BOOM_DASH_TICKS;
                state.boomInvuln = true;

                Vec3 launch = dir.scale(BOOM_SPEED);
                player.setDeltaMovement(launch.x, Math.max(launch.y, 0.05), launch.z);
                player.hasImpulse = true;

                // FIX: Sync initial dash velocity
                player.hurtMarked = true;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));

                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WARDEN_SONIC_BOOM,
                        player.getSoundSource(), 1.6f, 1.0f);
            }

            return;
        }

        // BIG PUSH
        if (state.boomDash > 0) {
            state.boomDash--;

            float pitch = player.getXRot();

            float yawRad = (float) Math.toRadians(state.boomYaw);
            float pitchRad = (float) Math.toRadians(pitch);

            double x = -Math.sin(yawRad) * Math.cos(pitchRad);
            double y = -Math.sin(pitchRad);
            double z =  Math.cos(yawRad) * Math.cos(pitchRad);

            Vec3 dir = new Vec3(x, y, z).normalize();

            double maxUp = 0.75;
            double maxDown = -0.85;
            dir = new Vec3(dir.x, Math.max(maxDown, Math.min(maxUp, dir.y)), dir.z).normalize();

            player.setDeltaMovement(dir.scale(BOOM_SPEED));
            player.hasImpulse = true;

            // FIX: Enforce dash velocity every tick
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));

            player.fallDistance = 0;
            player.startFallFlying();

            spawnBoomTunnel(world, player, dir);

            AABB box = player.getBoundingBox().inflate(BOOM_RADIUS);
            List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != player);

            // --- ENTITY COLLISION CHECK ---
            boolean hitEntity = false;
            AABB hitBox = player.getBoundingBox().inflate(0.8); // 0.8 block tolerance for direct impact

            for (LivingEntity e : nearby) {
                if (hitBox.intersects(e.getBoundingBox())) {
                    hitEntity = true;
                    break;
                }
            }

            // If we didn't get a direct hit, push nearby entities out of the way
            if (!hitEntity) {
                for (LivingEntity e : nearby) {
                    Vec3 away = e.position().subtract(player.position());
                    if (away.lengthSqr() < 0.0001) continue;
                    Vec3 knock = away.normalize().scale(0.9).add(0, 0.15, 0);
                    e.setDeltaMovement(e.getDeltaMovement().add(knock.x, knock.y, knock.z));
                    e.hasImpulse = true;

                    // FIX: Sync knockback for pushed players
                    if (e instanceof ServerPlayer targetPlayer) {
                        targetPlayer.hurtMarked = true;
                        targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
                    }
                }
            }

            // Calculate if we collided with terrain OR an entity
            boolean collided =
                    player.horizontalCollision
                            || player.verticalCollision
                            || player.onGround()
                            || boomHitsBlock(world, player)
                            || hitEntity;

            if (collided) {
                doBoomImpact(world, player);
                clearBoomState(state);

                if (player.isFallFlying()) player.stopFallFlying();
                return;
            }

            if (state.boomDash <= 0) {
                clearBoomState(state);
            }
        }
    }


    /* ============================================================
       PASSIVE TICK SECTIONS
       ============================================================ */

    private void tickPassiveFlight(ServerPlayer player, FlightState state) {
        if (player.isCreative() || player.isSpectator()) return;

        if (player.hasEffect(ModEffects.GROUNDED)) {
            if (player.isFallFlying()) player.stopFallFlying();
            state.flightActive = false;
            return;
        }

        if (player.isFallFlying() && player.isCrouching()) {
            player.stopFallFlying();
            state.flightActive = false;
            state.trailStep = 0;
            state.soundStep = 0;
            return;
        }

        if (state.glideRequest) {
            state.glideRequest = false;

            if (!player.onGround() && !player.isInWater() && !player.isFallFlying()) {
                player.startFallFlying();
                state.flightActive = true;
            }
        }

        state.flightActive = player.isFallFlying();
    }

    private void tickFlightFx(ServerPlayer player, FlightState state) {
        ServerLevel w = player.serverLevel();

        state.trailStep--;
        if (state.trailStep <= 0) {
            state.trailStep = TRAIL_INTERVAL;

            Vec3 vel = player.getDeltaMovement();
            Vec3 back = vel.lengthSqr() > 0.001 ? vel.normalize().scale(-0.6) : new Vec3(0, 0, 0);
            double x = player.getX() + back.x;
            double y = player.getY() + 0.6;
            double z = player.getZ() + back.z;

            w.sendParticles(ParticleTypes.CLOUD, x, y, z,
                    2, 0.08, 0.06, 0.08, 0.005);
        }

        state.soundStep--;
        if (state.soundStep <= 0) {
            state.soundStep = SOUND_INTERVAL;
            w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, player.getSoundSource(),
                    0.35f, 1.35f);
        }
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 33_000; }
    @Override public long getUltimateCooldownMs() { return 170_000; }

    /* ============================================================
       HELPERS
       ============================================================ */

    private void spawnBoomTunnel(ServerLevel w, ServerPlayer p, Vec3 dir) {
        Vec3 pos = p.position().add(0, 1.0, 0);
        Vec3 back = dir.scale(-1.0);

        for (int i = 0; i < 3; i++) {
            Vec3 pt = pos.add(back.scale(i * 1.5));

            w.sendParticles(ParticleTypes.CLOUD,
                    pt.x, pt.y, pt.z,
                    3, 0.4, 0.4, 0.4, 0.02);

            if (RNG.nextFloat() < 0.25f) {
                w.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        pt.x, pt.y, pt.z,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private boolean boomHitsBlock(ServerLevel world, ServerPlayer player) {
        Vec3 vel = player.getDeltaMovement();
        if (vel.lengthSqr() < 1.0e-4) return false;

        Vec3 start = player.position().add(0, 1.0, 0);
        Vec3 end = start.add(vel.normalize().scale(2.4));

        HitResult hit = world.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        return hit.getType() == HitResult.Type.BLOCK;
    }

    private void doBoomImpact(ServerLevel world, ServerPlayer player) {
        Vec3 c = player.position();

        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                player.getSoundSource(), 1.2f, 0.9f);

        float explodepower = 1.8f;

        world.explode(
                player,
                player.getX(), player.getY(), player.getZ(),
                explodepower,
                false,
                Level.ExplosionInteraction.BLOCK
        );

        AABB box = new AABB(c, c).inflate(BOOM_IMPACT_RADIUS);
        List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        CameraShake.shakeNearby(player,
                14,
                18,
                0.50f);

        for (LivingEntity e : targets) {
            e.hurt(com.loopy.loopypowers.damage.ModDamageTypes.sonic(player.serverLevel(), player), BOOM_IMPACT_DAMAGE);

            Vec3 away = e.position().subtract(c);
            if (away.lengthSqr() < 0.0001) away = new Vec3(0, 0, 1);
            Vec3 kb = away.normalize().scale(BOOM_IMPACT_KB).add(0, 0.45, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(kb.x, kb.y, kb.z));
            e.hasImpulse = true;

            if (e instanceof ServerPlayer targetPlayer) {
                targetPlayer.hurtMarked = true;
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
        }
        player.addEffect(new MobEffectInstance(ModEffects.GROUNDED, BOOM_KNOCKOUT_DURATION, 0, false, false, true));
        player.setDeltaMovement(0, Math.min(player.getDeltaMovement().y, -0.25), 0);
        player.hasImpulse = true;

        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private void clearBoomState(FlightState state) {
        state.boomDash = 0;
        state.boomInvuln = false;
        state.boomYaw = 0;
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.flight.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.flight.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.flight.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.flight.ultimate_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.flight.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.flight.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.flight.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.flight.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.flight.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.flight.description.ultimate").getString();
    }
}