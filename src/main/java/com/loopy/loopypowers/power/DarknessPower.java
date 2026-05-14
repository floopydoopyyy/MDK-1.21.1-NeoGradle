package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.RenderPackets;
import com.loopy.loopypowers.sound.ModSounds;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.entity.ShadowStepEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

public class DarknessPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, Integer> ACTIVE_MISTS = new HashMap<>();
    private boolean applyingDarknessDamage = false;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        removeDarknessTags(player);
        removeBlackoutNow(player.getServer(), player.getUUID());
        ACTIVE_MISTS.remove(player.getUUID());
        player.setNoGravity(false);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        removeDarknessTags(player);
        removeBlackoutNow(player.getServer(), player.getUUID());
        ACTIVE_MISTS.remove(player.getUUID());

        player.setNoGravity(false);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);   // StatusEffects.SPEED
        player.removeEffect(MobEffects.JUMP);              // StatusEffects.JUMP_BOOST
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.INVISIBILITY);
    }

    // Default method in interface now
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        handleMistForm(player);
        tickBlackoutsWorld(player.serverLevel()); // getServerWorld -> serverLevel
    }

    /* ============================================================
       DAMAGE HOOKS
       ============================================================ */

    // Ensure PowerInterface is updated to accept this hook signature!
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        // Mist form grants total immunity
        if (ACTIVE_MISTS.containsKey(victim.getUUID())) { // getUuid -> getUUID
            return false;
        }
        return true;
    }

    // Ensure PowerInterface is updated to accept this hook signature!
    public boolean onAttack(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0) return true;
        if (this.applyingDarknessDamage) return true; // recursion guard

        float mult = 1.0f;
        boolean didBackstab = false;
        boolean didExposed = false;
        DamageSource finalSource = source;
        ServerLevel w = attacker.serverLevel(); // getServerWorld -> serverLevel

        // Backstab logic
        if (isBehindTarget(attacker, target)) {
            mult *= BACKSTAB_BONUS_MULT;
            didBackstab = true;
            finalSource = ModDamageTypes.darknessBackstab(w, attacker);
        }

        // hasStatusEffect + registry entry wrapping -> hasEffect (DeferredHolder passed directly)
        if (target.hasEffect(ModEffects.EXPOSED)) {
            mult *= EXPOSED_DAMAGE_MULT;
            didExposed = true;
            finalSource = ModDamageTypes.darkUlt(w, attacker);
        }

        if (!didBackstab && !didExposed) return true;

        float newAmount = amount * mult;

        // damage -> hurt
        this.applyingDarknessDamage = true;
        target.hurt(finalSource, newAmount);
        this.applyingDarknessDamage = false;

        if (didBackstab) triggerBackstabFx(w, target, attacker);
        if (didExposed) triggerExposedFx(w, target, attacker);
        if (didBackstab && didExposed) triggerComboFx(w, target, attacker);

        return false; // cancel original vanilla hit
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final float BACKSTAB_BONUS_MULT = 1.30f; // +30%

    private static boolean isBehindTarget(LivingEntity attacker, LivingEntity victim) {
        if (attacker instanceof ServerPlayer player) {
            if (!PassiveManager.isEnabled(player)) return false;
        }

        // getRotationVec -> getViewVector
        Vec3 victimForward = victim.getViewVector(1.0f);
        Vec3 toAttacker = attacker.position().subtract(victim.position()); // getPos -> position

        victimForward = new Vec3(victimForward.x, 0.0, victimForward.z);
        toAttacker = new Vec3(toAttacker.x, 0.0, toAttacker.z);

        // lengthSquared -> lengthSqr
        if (victimForward.lengthSqr() < 1.0e-4 || toAttacker.lengthSqr() < 1.0e-4) {
            return false;
        }

        victimForward = victimForward.normalize();
        toAttacker = toAttacker.normalize();

        // dotProduct -> dot
        double dot = victimForward.dot(toAttacker);
        return dot < -0.35;
    }

    private void triggerBackstabFx(ServerLevel w, LivingEntity victim, LivingEntity attacker) {
        // spawnParticles -> sendParticles, getBlockPos -> blockPosition
        // getSoundCategory -> getSoundSource
        w.playSound(null, victim.blockPosition(), ModSounds.BACKSTAB.get(), attacker.getSoundSource(), 0.7f, 1.0f);
        w.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        w.sendParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 6, 0.2, 0.3, 0.2, 0.01);

        Vec3 dir = attacker.getViewVector(1.0f).normalize(); // getRotationVec -> getViewVector
        w.sendParticles(ParticleTypes.SMOKE, victim.getX() + dir.x * 0.5, victim.getY() + victim.getBbHeight() * 0.5, victim.getZ() + dir.z * 0.5, 6, 0.1, 0.1, 0.1, 0.01);
    }

    private void triggerExposedFx(ServerLevel w, LivingEntity victim, LivingEntity attacker) {
        w.playSound(null, victim.blockPosition(), ModSounds.BIGSTAB.get(), attacker.getSoundSource(), 0.5f, 1.2f);
        w.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 20, 0.4, 0.5, 0.4, 0.04);
        w.sendParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        w.sendParticles(BLACK_DUST, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 12, 0.25, 0.3, 0.25, 0.0);

        Vec3 dir = attacker.getViewVector(1.0f).normalize();
        for (int i = 0; i < 8; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double radius = 0.6;
            double px = victim.getX() + Math.cos(angle) * radius;
            double pz = victim.getZ() + Math.sin(angle) * radius;
            w.sendParticles(BLACK_DUST, px, victim.getY() + victim.getBbHeight() * 0.5, pz, 0, dir.x, 0.05, dir.z, 1.0);
        }
    }

    private void triggerComboFx(ServerLevel w, LivingEntity victim, LivingEntity attacker) {
        w.playSound(null, victim.blockPosition(), ModSounds.BIGSTAB.get(), attacker.getSoundSource(), 0.9f, 0.8f);
        w.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 15, 0.3, 0.3, 0.3, 0.1);

        if (victim instanceof ServerPlayer targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 3, 10, 0.06f);
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel(); // getServerWorld -> serverLevel

        // ModEntities.SHADOW_STEP static import removed; use .get() for DeferredHolder
        ShadowStepEntity proj = new ShadowStepEntity(ModEntities.SHADOW_STEP.get(), w);
        proj.setOwner(player);

        Vec3 look = player.getViewVector(1.0f); // getRotationVec -> getViewVector

        // setPosition -> setPos
        proj.setPos(
                player.getX(),
                player.getEyeY() - 0.1,
                player.getZ()
        );

        // setVelocity + Vec3d.multiply -> setDeltaMovement + Vec3.scale
        proj.setDeltaMovement(look.scale(1.2));
        proj.hasImpulse = true; // velocityModified -> hasImpulse

        w.addFreshEntity(proj); // spawnEntity -> addFreshEntity

        w.playSound(null, player.blockPosition(), // getBlockPos -> blockPosition
                ModSounds.DARKNESSTELEPORT.get(),
                player.getSoundSource(), // getSoundCategory -> getSoundSource
                0.6f, 1.4f);

        player.swing(InteractionHand.MAIN_HAND, true); // swingHand -> swing
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final int MIST_DURATION = 80;

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        RenderPackets.hidePlayerFromOthers(player, MIST_DURATION);

        // addStatusEffect -> addEffect, StatusEffects -> MobEffects
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MIST_DURATION, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, MIST_DURATION, 0, false, false, true));

        w.playSound(null, player.blockPosition(), ModSounds.MISTENTER.get(), player.getSoundSource(), 1.0f, 1.0f);

        // spawnParticles -> sendParticles, getBodyY(0.5) -> getY() + getBbHeight() * 0.5
        w.sendParticles(DARK_DUST, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 35, 0.8, 1.0, 0.8, 0.02);

        ACTIVE_MISTS.put(player.getUUID(), MIST_DURATION); // getUuid -> getUUID

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void handleMistForm(ServerPlayer player) {
        Integer ticks = ACTIVE_MISTS.get(player.getUUID());
        if (ticks == null) return;

        ticks--;

        if (ticks <= 0) {
            ACTIVE_MISTS.remove(player.getUUID());
            player.setNoGravity(false);
            return;
        }

        ACTIVE_MISTS.put(player.getUUID(), ticks);

        spawnMistTrail(player);

        if (ticks % 18 == 0) {
            player.serverLevel().playSound(
                    null,
                    player.blockPosition(),
                    ModSounds.MISTLOOP.get(),
                    SoundSource.PLAYERS, // SoundCategory.PLAYERS -> SoundSource.PLAYERS
                    1.0f, 2.0f
            );
        }

        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); // StatusEffects.SLOWNESS -> MobEffects.MOVEMENT_SLOWDOWN

        Vec3 look = player.getViewVector(1.0f); // getRotationVec -> getViewVector
        double speed = 0.6;

        // setVelocity + Vec3d.multiply -> setDeltaMovement + Vec3.scale
        Vec3 newVel = look.scale(speed);
        player.setDeltaMovement(newVel);
        player.hasImpulse = true; // velocityModified -> hasImpulse

        player.setOnGround(false);
        player.fallDistance = 0;
        player.setNoGravity(true);

        // getMainHandStack -> getMainHandItem, getItemCooldownManager().set -> getCooldowns().addCooldown
        if (!player.getMainHandItem().isEmpty()) {
            player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 5);
        }
        player.stopUsingItem();
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 5, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, true, false));
    }

    // DustParticleEffect -> DustParticleOptions, Vec3d.toVector3f() -> new Vector3f(...)
    private static final DustParticleOptions DARK_DUST =
            new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.05f), 1.4f);

    private static void spawnMistTrail(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        w.sendParticles(
                DARK_DUST,
                player.getX(),
                player.getY() + player.getBbHeight() * 0.5, // getBodyY(0.5) -> getY() + getBbHeight() * 0.5
                player.getZ(),
                12,
                0.6, 0.8, 0.6,
                0.01
        );
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    // SIZE
    private static final int BLACKOUT_RADIUS = 16;
    private static final double BLACKOUT_HEIGHT = 13.0;

    // TIMING
    private static final int BLACKOUT_DURATION_TICKS = 20 * 10;
    private static final int BLACKOUT_APPLY_EVERY_TICKS = 5;
    private static final int BLACKOUT_FX_EVERY_TICKS = 3;

    // VISUALS
    private static final int BLACKOUT_RING_POINTS = 48;
    private static final int BLACKOUT_VERTICAL_LAYERS = 20;
    private static final int BLACKOUT_INNER_PARTICLES = 100;
    private static final double BLACKOUT_INNER_SPREAD = BLACKOUT_RADIUS * 0.9;
    // multiplier
    private static final float EXPOSED_DAMAGE_MULT = 1.60f;
    // EGG
    private static final float FUNNY_SOUND_CHANCE = 0.0005f;

    // DustParticleEffect -> DustParticleOptions, Vec3d.toVector3f() -> new Vector3f(...)
    private static final DustParticleOptions BLACK_DUST =
            new DustParticleOptions(new Vector3f(0.01f, 0.01f, 0.01f), 1.8f);

    private static final Map<UUID, BlackoutState> ACTIVE_BLACKOUTS = new HashMap<>();
    // RegistryKey<World> -> ResourceKey<Level>
    private static final Map<ResourceKey<Level>, Long> BLACKOUT_LAST_TICK = new HashMap<>();

    private static final class BlackoutState {
        final UUID owner;
        final ResourceKey<Level> worldKey; // RegistryKey<World> -> ResourceKey<Level>
        final Vec3 center;                 // Vec3d -> Vec3
        int ticksLeft;

        BlackoutState(UUID owner, ResourceKey<Level> worldKey, Vec3 center, int ticksLeft) {
            this.owner = owner;
            this.worldKey = worldKey;
            this.center = center;
            this.ticksLeft = ticksLeft;
        }
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        removeBlackoutNow(player.getServer(), player.getUUID());

        BlackoutState st = new BlackoutState(
                player.getUUID(),
                w.dimension(),        // getRegistryKey -> dimension
                player.position(),    // getPos -> position
                BLACKOUT_DURATION_TICKS
        );

        ACTIVE_BLACKOUTS.put(player.getUUID(), st);

        // SoundEvents.BLOCK_END_PORTAL_SPAWN -> SoundEvents.END_PORTAL_SPAWN (removed BLOCK_ prefix)
        // SoundCategory.PLAYERS -> SoundSource.PLAYERS
        w.playSound(null, player.blockPosition(),
                SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS,
                0.9f, 0.6f);

        w.sendParticles(ParticleTypes.LARGE_SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                40, 1.0, 0.7, 1.0, 0.03);

        w.sendParticles(ParticleTypes.SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                55, 2.0, 1.0, 2.0, 0.01);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void tickBlackoutsWorld(ServerLevel w) {
        long now = w.getGameTime(); // getTime -> getGameTime
        ResourceKey<Level> key = w.dimension(); // getRegistryKey -> dimension

        Long last = BLACKOUT_LAST_TICK.get(key);
        if (last != null && last == now) return;
        BLACKOUT_LAST_TICK.put(key, now);

        if (ACTIVE_BLACKOUTS.isEmpty()) return;

        List<UUID> toRemove = new ArrayList<>();

        for (var entry : new ArrayList<>(ACTIVE_BLACKOUTS.entrySet())) {
            UUID owner = entry.getKey();
            BlackoutState st = entry.getValue();

            if (!st.worldKey.equals(key)) continue;

            st.ticksLeft--;
            if (st.ticksLeft <= 0) {
                toRemove.add(owner);
                continue;
            }

            if ((now % BLACKOUT_APPLY_EVERY_TICKS) == 0L) {
                applyBlackoutEffects(w, st);
            }

            if ((now % BLACKOUT_FX_EVERY_TICKS) == 0L) {
                spawnBlackoutFx(w, st);
            }

            if ((now % 25L) == 0L) {
                playDarknessLoop(w, st);
            }

            // --- EGG ---
            if (w.random.nextFloat() < FUNNY_SOUND_CHANCE) {
                tryPlayFunnySound(w, st);
            }
        }

        for (UUID owner : toRemove) {
            removeBlackoutNow(w.getServer(), owner);
        }
    }

    private static void applyBlackoutEffects(ServerLevel w, BlackoutState st) {
        // Box -> AABB, getEntitiesByClass -> getEntitiesOfClass
        AABB box = new AABB(
                st.center.x - BLACKOUT_RADIUS, st.center.y - BLACKOUT_HEIGHT, st.center.z - BLACKOUT_RADIUS,
                st.center.x + BLACKOUT_RADIUS, st.center.y + BLACKOUT_HEIGHT, st.center.z + BLACKOUT_RADIUS
        );

        List<LivingEntity> entities = w.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);

        double radiusSq = BLACKOUT_RADIUS * BLACKOUT_RADIUS;

        for (LivingEntity e : entities) {
            if (e.getUUID().equals(st.owner)) continue; // getUuid -> getUUID

            double dx = e.getX() - st.center.x;
            double dz = e.getZ() - st.center.z;
            double distSq = dx * dx + dz * dz;

            if (distSq > radiusSq) continue;

            // addStatusEffect -> addEffect, StatusEffects -> MobEffects
            // Registries.STATUS_EFFECT.getEntry(ModEffects.EXPOSED) -> ModEffects.EXPOSED (DeferredHolder passed directly)
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false));
            e.addEffect(new MobEffectInstance(ModEffects.EXPOSED, 45, 0, true, false));
        }
    }

    private static void spawnBlackoutFx(ServerLevel w, BlackoutState st) {

        // ===== CENTER =====
        w.sendParticles(ParticleTypes.SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                12,
                2.0, 1.5, 2.0,
                0.01);

        w.sendParticles(ParticleTypes.LARGE_SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                6,
                1.5, 1.0, 1.5,
                0.01);

        // ===== INTERIOR =====
        for (int i = 0; i < BLACKOUT_INNER_PARTICLES; i++) {

            double angle = w.random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(w.random.nextDouble()) * BLACKOUT_INNER_SPREAD;

            double x = st.center.x + Math.cos(angle) * radius;
            double z = st.center.z + Math.sin(angle) * radius;
            double y = st.center.y + (w.random.nextDouble() * BLACKOUT_HEIGHT * 2 - BLACKOUT_HEIGHT);

            w.sendParticles(ParticleTypes.SMOKE,
                    x, y, z,
                    1,
                    0.05, 0.05, 0.05,
                    0.005);

            if (w.random.nextFloat() < 0.35f) {
                w.sendParticles(BLACK_DUST,
                        x, y, z,
                        1,
                        0.02, 0.02, 0.02,
                        0.0);
            }
        }

        // ===== OUTER =====
        for (int y = 0; y < BLACKOUT_VERTICAL_LAYERS; y++) {

            double heightOffset = ((double) y / (BLACKOUT_VERTICAL_LAYERS - 1)) * BLACKOUT_HEIGHT * 2 - BLACKOUT_HEIGHT;
            double layerRadius = BLACKOUT_RADIUS * Math.sqrt(1 - Math.pow(heightOffset / BLACKOUT_HEIGHT, 2));

            for (int i = 0; i < BLACKOUT_RING_POINTS; i++) {

                double angle = (Math.PI * 2.0 * i) / BLACKOUT_RING_POINTS;

                double x = st.center.x + Math.cos(angle) * layerRadius;
                double z = st.center.z + Math.sin(angle) * layerRadius;
                double yPos = st.center.y + heightOffset;

                w.sendParticles(ParticleTypes.SMOKE,
                        x, yPos, z,
                        1,
                        0.1, 0.1, 0.1,
                        0.0);

                if (i % 4 == 0) {
                    w.sendParticles(ParticleTypes.LARGE_SMOKE,
                            x, yPos, z,
                            1,
                            0.05, 0.05, 0.05,
                            0.0);
                }
            }
        }
    }

    private static void removeBlackoutNow(MinecraftServer server, UUID owner) {
        if (server == null) return;

        BlackoutState st = ACTIVE_BLACKOUTS.remove(owner);
        if (st == null) return;

        // server.getWorld(key) -> server.getLevel(key)
        ServerLevel w = server.getLevel(st.worldKey);
        if (w == null) return;

        // BlockPos.ofFloored -> BlockPos.containing, SoundEvents.BLOCK_FIRE_EXTINGUISH -> SoundEvents.FIRE_EXTINGUISH
        // SoundCategory.PLAYERS -> SoundSource.PLAYERS
        w.playSound(null, BlockPos.containing(st.center),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS,
                0.8f, 0.65f);

        w.sendParticles(ParticleTypes.CLOUD,
                st.center.x, st.center.y + 1.0, st.center.z,
                25, 1.0, 0.6, 1.0, 0.03);
    }

    private static void playDarknessLoop(ServerLevel world, BlackoutState st) {
        double radius = BLACKOUT_RADIUS;
        double radiusSq = radius * radius;

        // world.getPlayers() -> world.players()
        for (ServerPlayer p : world.players()) {

            double dx = p.getX() - st.center.x;
            double dz = p.getZ() - st.center.z;

            if ((dx * dx + dz * dz) > radiusSq) continue;

            world.playSound(
                    null,
                    p.blockPosition(),
                    ModSounds.DARKNESSLOOP.get(),
                    p.getSoundSource(),
                    0.6f,
                    1.0f
            );
        }
    }

    // EGG
    private static void tryPlayFunnySound(ServerLevel w, BlackoutState st) {
        List<ServerPlayer> playersInside = new ArrayList<>();
        double radiusSq = BLACKOUT_RADIUS * BLACKOUT_RADIUS;

        // world.getPlayers() -> world.players(), squaredDistanceTo -> distanceToSqr
        for (ServerPlayer p : w.players()) {
            if (p.distanceToSqr(st.center) <= radiusSq) {
                playersInside.add(p);
            }
        }

        if (!playersInside.isEmpty()) {
            ServerPlayer victim = playersInside.get(w.random.nextInt(playersInside.size()));

            w.playSound(
                    null,
                    victim.blockPosition(),
                    ModSounds.FUNNYFNAF.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 14_000; }
    @Override public long getSecondaryCooldownMs() { return 25_000; }
    @Override public long getUltimateCooldownMs() { return 320_000; }

    /* ============================================================
       DISPLAY
       ============================================================ */

    // Text.translatable -> Component.translatable
    @Override public String getName() { return Component.translatable("power.loopypowers.darkness.name").getString(); }
    @Override public String getPassiveName() { return Component.translatable("power.loopypowers.darkness.passive_name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.darkness.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.darkness.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.darkness.ultimate_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.darkness.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.darkness.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.darkness.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.darkness.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.darkness.description.ultimate").getString();
    }

    /* ============================================================
       TAG HELPERS
       ============================================================ */

    // getCommandTags -> getTags
    private static void removeDarknessTags(Entity e) {
        var it = e.getTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith("dk_")) {
                it.remove();
                return;
            }
        }
    }
}