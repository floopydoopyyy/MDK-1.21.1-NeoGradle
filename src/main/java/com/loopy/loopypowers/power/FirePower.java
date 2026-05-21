package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.entity.PowerFireballEntity;
import com.loopy.loopypowers.manager.AbilityTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.FireUltPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FirePower implements PowerInterface {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, FireState> ACTIVE_STATES = new HashMap<>();

    private static class FireState {
        int hoverTicks    = 0;
        int ultChargeTicks = 0;
    }

    private static FireState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new FireState());
    }

    /* ============================================================
       CONSTANTS - PASSIVE
       ============================================================ */

    private static final int PASSIVE_FIRE_RESIST_DURATION = 600;
    private static final int PASSIVE_FIRE_ON_HIT_DURATION = 4;

    /* ============================================================
       CONSTANTS - PRIMARY
       ============================================================ */

    private static final float  PRIMARY_SPEED            = 2.6f;
    private static final float  PRIMARY_DIRECT_DAMAGE    = 9.5f;
    private static final int    PRIMARY_EXPLOSION_POWER  = 3;
    private static final float  PRIMARY_EXPLOSION_DAMAGE = 8.8f;
    private static final double PRIMARY_SPAWN_OFFSET     = 0.6;

    /* ============================================================
       CONSTANTS - SECONDARY
       ============================================================ */

    private static final float  SECONDARY_EXPLOSION_POWER   = 2.5f;
    private static final float  SECONDARY_EXPLOSION_DAMAGE  = 13.5f;
    private static final double SECONDARY_LAUNCH_STRENGTH   = 1.7;
    private static final double SECONDARY_KB_HORIZONTAL     = 1.7;
    private static final double SECONDARY_KB_VERTICAL       = 0.5;
    private static final int    SECONDARY_FIRE_DURATION     = 4;
    private static final int    SECONDARY_HOVER_TICKS       = 140;
    private static final int    SECONDARY_NO_FALL_TICKS     = 200;

    private static final float  COOK_FOOD_CHANCE            = 0.35f;

    // Hover mechanics
    private static final double HOVER_LIFT_FORCE            = 0.045;
    private static final double HOVER_GRAVITY_LIMIT         = -0.08;
    private static final double HOVER_AIR_CONTROL           = 0.99;
    private static final double HOVER_STEERING_FORCE        = 0.035;

    // Camera shake
    private static final int    SECONDARY_SHAKE_DURATION    = 20;
    private static final int    SECONDARY_SHAKE_AMPLITUDE   = 10;
    private static final float  SECONDARY_SHAKE_INTENSITY   = 1.0f;

    private static final int    SECONDARY_EXPLOSION_SHAKE_DUR = 6;
    private static final int    SECONDARY_EXPLOSION_SHAKE_AMP = 8;
    private static final float  SECONDARY_EXPLOSION_SHAKE_INT = 0.25f;

    /* ============================================================
       CONSTANTS - ULTIMATE
       ============================================================ */

    private static final int    ULTIMATE_CHARGE_TICKS          = 100;
    private static final float  ULTIMATE_EXPLOSION_POWER       = 10.0f;
    private static final float  ULTIMATE_DAMAGE_RADIUS         = 12.0f;
    private static final float  ULTIMATE_MAX_DAMAGE            = 40.0f;
    private static final int    ULTIMATE_FIRE_DURATION         = 6;

    private static final double ULTIMATE_PULL_BASE_RADIUS      = 6.0;
    private static final double ULTIMATE_PULL_SCALED_RADIUS    = 14.0;
    private static final double ULTIMATE_PULL_BASE_STRENGTH    = 0.01;
    private static final double ULTIMATE_PULL_SCALED_STRENGTH  = 0.04;
    private static final double ULTIMATE_PULL_VERTICAL_MODIFIER = 0.2;

    private static final int    ULT_START_SHAKE_RADIUS         = 30;
    private static final int    ULT_START_SHAKE_TIME           = 100;
    private static final float  ULT_START_SHAKE_INTENSITY      = 0.6f;

    private static final int    ULT_DETONATE_SHAKE_DURATION    = 50;
    private static final int    ULT_DETONATE_SHAKE_AMPLITUDE   = 30;
    private static final float  ULT_DETONATE_SHAKE_INTENSITY   = 2.5f;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("fire_"));
        ACTIVE_STATES.put(player.getUUID(), new FireState());

        player.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, PASSIVE_FIRE_RESIST_DURATION, 0, true, false));
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("fire_"));
        ACTIVE_STATES.remove(player.getUUID());

        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.removeEffect(ModEffects.BRACED);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FireUltPayload(player.getId(), false));
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        FireState state = getState(player);

        // PASSIVE — keep fire resistance topped up
        if (PassiveManager.isEnabled(player)) {
            MobEffectInstance fireRes = player.getEffect(MobEffects.FIRE_RESISTANCE);
            if (fireRes == null || fireRes.getDuration() < 100) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.FIRE_RESISTANCE, PASSIVE_FIRE_RESIST_DURATION, 0, true, false));
            }
        }

        // SECONDARY hover tick
        if (state.hoverTicks > 0) {
            tickHover(player, state);
        }

        // ULTIMATE charge tick
        if (state.ultChargeTicks > 0) {
            tickUltimateCharge(player, state);
        }
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;
        target.igniteForSeconds(PASSIVE_FIRE_ON_HIT_DURATION);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)) return;

        shootFireball(world, player, PRIMARY_SPEED, PRIMARY_DIRECT_DAMAGE,
                PRIMARY_EXPLOSION_POWER, PRIMARY_EXPLOSION_DAMAGE);
    }

    private void shootFireball(
            ServerLevel world,
            ServerPlayer player,
            float speed,
            float directDamage,
            int explosionPower,
            float explosionDamage
    ) {
        Vec3 look = player.getViewVector(1.0f);

        PowerFireballEntity fireball = new PowerFireballEntity(
                ModEntities.POWER_FIREBALL.get(),
                world,
                player,
                look.x * speed,
                look.y * speed,
                look.z * speed,
                explosionPower
        );

        fireball.setDamageValues(directDamage, explosionDamage);

        Vec3 spawnPos = player.getEyePosition().add(look.scale(PRIMARY_SPAWN_OFFSET));
        fireball.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, player.getYRot(), player.getXRot());

        player.swing(InteractionHand.MAIN_HAND, true);
        world.addFreshEntity(fireball);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        launchExplosion(player);

        FireState state     = getState(player);
        state.hoverTicks    = SECONDARY_HOVER_TICKS;

        if (player.serverLevel().random.nextFloat() < COOK_FOOD_CHANCE) {
            tryCookSnack(player);
        }

        CameraShake.shakeNearby(player, SECONDARY_SHAKE_DURATION, SECONDARY_SHAKE_AMPLITUDE, SECONDARY_SHAKE_INTENSITY);
        PowerManager.clearAbilityCooldown(player, AbilityTypes.PRIMARY);
    }

    private void tryCookSnack(ServerPlayer player) {
        InteractionHand[] hands = { InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND };
        for (InteractionHand hand : hands) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) continue;

            Item cooked = getCookedVariant(stack.getItem());
            if (cooked != null) {
                stack.shrink(1);
                player.getInventory().placeItemBackInInventory(new ItemStack(cooked));
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 2.0f);
                break;
            }
        }
    }

    private Item getCookedVariant(Item raw) {
        if (raw == Items.BEEF)    return Items.COOKED_BEEF;
        if (raw == Items.PORKCHOP) return Items.COOKED_PORKCHOP;
        if (raw == Items.CHICKEN) return Items.COOKED_CHICKEN;
        if (raw == Items.MUTTON)  return Items.COOKED_MUTTON;
        if (raw == Items.RABBIT)  return Items.COOKED_RABBIT;
        if (raw == Items.COD)     return Items.COOKED_COD;
        if (raw == Items.SALMON)  return Items.COOKED_SALMON;
        if (raw == Items.POTATO)  return Items.BAKED_POTATO;
        if (raw == Items.KELP)    return Items.DRIED_KELP;
        return null;
    }

    private void launchExplosion(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        DamageSource explosionSource = ModDamageTypes.firePower(world, player);

        world.explode(player, explosionSource, null,
                player.getX(), player.getY(), player.getZ(),
                SECONDARY_EXPLOSION_POWER, true, Level.ExplosionInteraction.MOB);

        CameraShake.shakeNearby(player,
                SECONDARY_EXPLOSION_SHAKE_DUR,
                SECONDARY_EXPLOSION_SHAKE_AMP,
                SECONDARY_EXPLOSION_SHAKE_INT);

        player.setDeltaMovement(
                player.getDeltaMovement().x * 0.7,
                SECONDARY_LAUNCH_STRENGTH,
                player.getDeltaMovement().z * 0.7
        );
        player.hasImpulse = true;
        player.hurtMarked  = true;

        player.fallDistance = 0;

        player.addEffect(new MobEffectInstance(
                ModEffects.BRACED, SECONDARY_NO_FALL_TICKS, 0, true, false, true));

        world.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(4),
                e -> e != player
        ).forEach(entity -> {
            Vec3 dir = entity.position().subtract(player.position()).normalize();

            entity.setDeltaMovement(entity.getDeltaMovement()
                    .add(dir.x * SECONDARY_KB_HORIZONTAL, SECONDARY_KB_VERTICAL, dir.z * SECONDARY_KB_HORIZONTAL));
            entity.hasImpulse = true;
            entity.hurtMarked  = true; // explicit sync; don't rely solely on hurt() setting this

            entity.hurt(explosionSource, SECONDARY_EXPLOSION_DAMAGE);
            entity.igniteForSeconds(SECONDARY_FIRE_DURATION);
        });
    }

    private void tickHover(ServerPlayer player, FireState state) {
        if (player.onGround() || player.isCrouching()) {
            state.hoverTicks = 0;
            return;
        }

        Vec3 vel  = player.getDeltaMovement();
        double newY = vel.y;

        if (newY < 0.25)            newY += HOVER_LIFT_FORCE;
        if (newY < HOVER_GRAVITY_LIMIT) newY = HOVER_GRAVITY_LIMIT;

        player.setDeltaMovement(vel.x * HOVER_AIR_CONTROL, newY, vel.z * HOVER_AIR_CONTROL);

        Vec3 look = player.getViewVector(1.0f);
        player.setDeltaMovement(player.getDeltaMovement().add(
                look.x * HOVER_STEERING_FORCE, 0, look.z * HOVER_STEERING_FORCE));

        player.hasImpulse = true;
        player.hurtMarked  = true;

        player.fallDistance = 0;

        spawnHoverParticles(player);
        state.hoverTicks--;
    }

    private void spawnHoverParticles(ServerPlayer player) {
        var world = player.serverLevel();

        world.sendParticles(ParticleTypes.FLAME,
                player.getX(), player.getY() - 0.4, player.getZ(),
                8, 0.25, 0.1, 0.25, 0.02);

        world.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() - 0.4, player.getZ(),
                4, 0.2, 0.05, 0.2, 0.01);
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        FireState state    = getState(player);
        state.ultChargeTicks = ULTIMATE_CHARGE_TICKS;

        CameraShake.shakeNearby(player, ULT_START_SHAKE_RADIUS, ULT_START_SHAKE_TIME, ULT_START_SHAKE_INTENSITY);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, player.getSoundSource(), 1.0f, 0.6f);

        // Tell clients to start drawing particles locally
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FireUltPayload(player.getId(), true));
    }

    private void tickUltimateCharge(ServerPlayer player, FireState state) {
        var world = player.serverLevel();

        state.ultChargeTicks--;

        if (state.ultChargeTicks <= 0) {
            detonateUltimate(player);
            return;
        }

        float progress = 1f - (state.ultChargeTicks / (float) ULTIMATE_CHARGE_TICKS);
        applyUltimateChargeEffects(player, world, progress, state.ultChargeTicks);
    }

    private void applyUltimateChargeEffects(
            ServerPlayer player,
            ServerLevel world,
            float progress,
            int ticksRemaining
    ) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 4, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,  5, 3, true, false));

        player.fallDistance = 0;

        CameraShake.shakeNearby(player, 20, 2, 0.15f + progress * 0.6f);

        pullEntitiesToward(player, world, progress);

        if (ticksRemaining % 20 == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_AMBIENT, player.getSoundSource(), 1.5f, 0.5f + progress);
        }

        if (ticksRemaining < 20) {
            CameraShake.shakeNearby(player, 60, 3, 1.2f);
        }
    }

    private void pullEntitiesToward(ServerPlayer player, ServerLevel world, float progress) {
        double radius = ULTIMATE_PULL_BASE_RADIUS + progress * ULTIMATE_PULL_SCALED_RADIUS;

        for (LivingEntity entity : world.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player
        )) {
            Vec3 dir = player.position().subtract(entity.position());
            if (dir.length() < 0.1) continue;

            dir = dir.normalize();
            double strength = ULTIMATE_PULL_BASE_STRENGTH + progress * ULTIMATE_PULL_SCALED_STRENGTH;

            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    dir.x * strength,
                    dir.y * strength * ULTIMATE_PULL_VERTICAL_MODIFIER,
                    dir.z * strength
            ));
            entity.hasImpulse = true;
            entity.hurtMarked  = true; // sync velocity to all tracking clients, not just ServerPlayers
        }
    }

    private void detonateUltimate(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        DamageSource source = ModDamageTypes.fireExplosion(world, player);

        // Turn off client particles
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new FireUltPayload(player.getId(), false));

        world.explode(player, source, null,
                player.getX(), player.getY(), player.getZ(),
                ULTIMATE_EXPLOSION_POWER, true, Level.ExplosionInteraction.MOB);

        applyLOSExplosionDamage(player, ULTIMATE_DAMAGE_RADIUS, ULTIMATE_MAX_DAMAGE);

        CameraShake.shakeNearby(player, ULT_DETONATE_SHAKE_DURATION,
                ULT_DETONATE_SHAKE_AMPLITUDE, ULT_DETONATE_SHAKE_INTENSITY);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, player.getSoundSource(), 3.5f, 0.6f);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), player.getSoundSource(), 4.0f, 0.5f);
    }

    private void applyLOSExplosionDamage(ServerPlayer sourcePlayer, float radius, float maxDamage) {
        ServerLevel world  = sourcePlayer.serverLevel();
        Vec3        origin = sourcePlayer.position();
        AABB        area   = new AABB(origin, origin).inflate(radius);

        DamageSource source = ModDamageTypes.fireExplosion(world, sourcePlayer);

        for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, area, e -> e != sourcePlayer)) {
            Vec3 target = entity.position().add(0, entity.getBbHeight() * 0.5, 0);

            var hit = world.clip(new ClipContext(origin, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sourcePlayer));

            if (hit.getType() == HitResult.Type.BLOCK) continue;

            double dist    = origin.distanceTo(target);
            double falloff = 1.0 - (dist / radius);
            if (falloff <= 0) continue;

            entity.igniteForSeconds(ULTIMATE_FIRE_DURATION);
            entity.hurt(source, (float)(maxDamage * falloff));
        }
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override public long getPrimaryCooldownMs()   { return 8_500; }
    @Override public long getSecondaryCooldownMs() { return 45_000; }
    @Override public long getUltimateCooldownMs()  { return 500_000; }

    @Override public String getName()            { return Component.translatable("power.loopypowers.fire.name").getString(); }
    @Override public String getPassiveName()     { return Component.translatable("power.loopypowers.fire.passive_name").getString(); }
    @Override public String getPrimaryName()     { return Component.translatable("power.loopypowers.fire.primary_name").getString(); }
    @Override public String getSecondaryName()   { return Component.translatable("power.loopypowers.fire.secondary_name").getString(); }
    @Override public String getUltimateName()    { return Component.translatable("power.loopypowers.fire.ultimate_name").getString(); }

    @Override public String getOverviewDescription()   { return Component.translatable("power.loopypowers.fire.description.overview").getString(); }
    @Override public String getPassiveDescription()    { return Component.translatable("power.loopypowers.fire.description.passive").getString(); }
    @Override public String getPrimaryDescription()    { return Component.translatable("power.loopypowers.fire.description.primary").getString(); }
    @Override public String getSecondaryDescription()  { return Component.translatable("power.loopypowers.fire.description.secondary").getString(); }
    @Override public String getUltimateDescription()   { return Component.translatable("power.loopypowers.fire.description.ultimate").getString(); }
}