package com.loopy.loopypowers.power;

import com.loopy.loopypowers.ui.CooldownUI;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.loopy.loopypowers.network.RenderPackets;
import net.neoforged.neoforge.network.PacketDistributor;
import com.loopy.loopypowers.network.payload.TeleportParticlePayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.loopy.loopypowers.ui.CooldownUI.makeChargeSuffix;
import static com.loopy.loopypowers.ui.CooldownUI.setCooldownEnd;

public class TeleportPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, TeleportState> ACTIVE_STATES = new HashMap<>();

    private static class TeleportState {
        int blinkCharges = PRIMARY_MAX_CHARGES;
        int blinkRechargeTicks = -1;
        int blinkLockTicks = 0;

        int dodgeCdTicks = 0;
        int phaseTicks = 0;

        int frenzyTicks = 0;
        int frenzyStep = 0;
    }

    private static TeleportState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new TeleportState());
    }

    /* ============================================================
       CONSTANTS - PASSIVE
       ============================================================ */
    private static final float PASSIVE_DODGE_CHANCE = 0.18f; // percentage chance to dodge
    private static final int PASSIVE_HIDE_TICKS = 12;        // how long dodge invisibility effect is
    private static final int PASSIVE_IFRAME_TICKS = 18;      // window of invincibility
    private static final int PASSIVE_COOLDOWN_TICKS = 160;   // dodge cooldown in ticks

    /* ============================================================
       CONSTANTS - PRIMARY
       ============================================================ */
    private static final int PRIMARY_MAX_CHARGES = 3;
    private static final int PRIMARY_RECHARGE_TICKS = 120;           // 6 seconds per charge
    private static final int PRIMARY_LOCK_TICKS = 5;                 // tiny anti-spam delay between blinks
    private static final double PRIMARY_BLINK_DIST = 14.0;           // distance of the blink
    private static final double PRIMARY_AIR_LOOK_THRESHOLD = 0.55;   // decides if angle is high enough for air teleport

    /* ============================================================
       CONSTANTS - SECONDARY
       ============================================================ */
    private static final long SECONDARY_COOLDOWN_MS = 16_000;
    private static final double SECONDARY_RANGE = 24.0;
    private static final double SECONDARY_HIT_MARGIN = 2.0;          // How generous the hitbox is

    /* ============================================================
       CONSTANTS - ULTIMATE
       ============================================================ */
    private static final long ULTIMATE_COOLDOWN_MS = 420_000;
    private static final int ULTIMATE_DURATION_TICKS = 120;       // 6 seconds
    private static final int ULTIMATE_ATTACK_STEP_INITIAL = 2;    // startup delay before first swing
    private static final int ULTIMATE_ATTACK_STEP_ONGOING = 4;    // ticks between each swing
    private static final double ULTIMATE_SEARCH_RADIUS = 7.0;     // how far to look for targets
    private static final double ULTIMATE_TELEPORT_OFFSET = -1.5;  // how far behind victim to teleport

    private static final Random RNG = new Random();

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("tp_")); // Clean legacy tags
        ACTIVE_STATES.put(player.getUUID(), new TeleportState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("tp_"));
        ACTIVE_STATES.remove(player.getUUID());
        player.removeEffect(MobEffects.DIG_SPEED);
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        TeleportState state = getState(player);

        // Primary Charges
        if (state.blinkLockTicks > 0) state.blinkLockTicks--;
        tickBlinkRecharge(player, state);
        updateBlinkCooldownUI(player, state);

        // tick timers
        if (state.dodgeCdTicks > 0) state.dodgeCdTicks--;
        if (state.phaseTicks > 0) state.phaseTicks--;

        if (state.frenzyTicks > 0) {
            tickFrenzy(player, state);
        }
    }

    // =========================
    // PASSIVE
    // =========================

    @Override
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        return !tryDodge(victim);
    }

    public boolean tryDodge(ServerPlayer player) {
        if (!PassiveManager.isEnabled(player)) return false;

        TeleportState state = getState(player);

        if (state.dodgeCdTicks > 0) return false;
        if (RNG.nextFloat() > PASSIVE_DODGE_CHANCE) return false;

        RenderPackets.hidePlayerFromOthers(player, PASSIVE_HIDE_TICKS);
        state.phaseTicks = PASSIVE_IFRAME_TICKS;
        state.dodgeCdTicks = PASSIVE_COOLDOWN_TICKS;

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TeleportParticlePayload(
                TeleportParticlePayload.DODGE, player.getX(), player.getY(), player.getZ(), 0, 0, 0
        ));

        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, player.getSoundSource(), 1.0f, 1.2f);

        return true;
    }

    // =========================
    // PRIMARY
    // =========================

    @Override
    public void activatePrimary(ServerPlayer player) {
        TeleportState state = getState(player);
        if (state.blinkLockTicks > 0) return;
        if (state.blinkCharges <= 0) return;

        state.blinkCharges--;
        state.blinkLockTicks = PRIMARY_LOCK_TICKS;

        if (state.blinkRechargeTicks < 0) {
            state.blinkRechargeTicks = PowerManager.getModifiedCooldownTicks(player, PRIMARY_RECHARGE_TICKS);
        }

        blinkForward(player);
    }

    private static void tickBlinkRecharge(ServerPlayer player, TeleportState state) {
        if (PowerManager.areCooldownsDisabled()) {
            state.blinkCharges = PRIMARY_MAX_CHARGES;
            state.blinkRechargeTicks = -1;
            return;
        }

        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            state.blinkRechargeTicks = -1;
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, PRIMARY_RECHARGE_TICKS);

        if (state.blinkRechargeTicks <= 0 && state.blinkRechargeTicks != -1) {
            state.blinkRechargeTicks = maxTicks;
        }

        if (state.blinkRechargeTicks >= 0) {
            state.blinkRechargeTicks--;

            if (state.blinkRechargeTicks <= 0) {
                state.blinkCharges++;

                if (state.blinkCharges < PRIMARY_MAX_CHARGES) {
                    state.blinkRechargeTicks = maxTicks;
                } else {
                    state.blinkRechargeTicks = -1;
                }
            }
        }
    }

    private static void updateBlinkCooldownUI(ServerPlayer player, TeleportState state) {
        String key = "Teleport:PRIMARY";

        if (state.blinkCharges >= PRIMARY_MAX_CHARGES || PowerManager.areCooldownsDisabled()) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, PRIMARY_RECHARGE_TICKS);

        int leftTicks = state.blinkRechargeTicks;
        if (leftTicks < 0) leftTicks = maxTicks;

        long endMs = System.currentTimeMillis() + (leftTicks * 50L);

        String suffix = makeChargeSuffix(
                state.blinkCharges, PRIMARY_MAX_CHARGES, leftTicks, Math.max(1, maxTicks)
        ).getString();

        setCooldownEnd(player, key, endMs, Component.literal(suffix));
    }

    private static void blinkForward(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);

        double distanceMultiplier = 1.0;
        if (look.y < 0) {
            distanceMultiplier = Math.max(0.2, 1.0 + look.y);
        }

        double actualDistance = PRIMARY_BLINK_DIST * distanceMultiplier;

        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;
        if (!allowAir) {
            look = new Vec3(look.x, 0.0, look.z);
            if (look.lengthSqr() < 1.0e-6) return;
            look = look.normalize();
        }

        Vec3 desired = eye.add(look.scale(actualDistance));

        if (!allowAir) {
            desired = new Vec3(desired.x, player.getY(), desired.z);
        }

        Vec3 safe = findSafeTeleportSpot(world, player, desired);
        Vec3 origin = player.position();

        world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 1.0f, 1.2f);
        world.playSound(null, player.blockPosition(), ModSounds.TELEPORTSNAP.get(), player.getSoundSource(), 0.7f, 1.4f);

        safeTeleport(player, safe.x, safe.y, safe.z);

        // CLIENT DISPATCH: Blink Trail and Bursts
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TeleportParticlePayload(
                TeleportParticlePayload.BLINK, origin.x, origin.y, origin.z, safe.x, safe.y, safe.z
        ));

        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 5, 100, true, false, false));
    }

    private static Vec3 findSafeTeleportSpot(ServerLevel world, ServerPlayer player, Vec3 desired) {
        BlockPos base = BlockPos.containing(desired.x, desired.y, desired.z);

        Vec3 look = player.getViewVector(1.0f);
        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;

        int[] order = new int[] { 0, 1, -1, 2, -2, 3, -3 };

        for (int dy : order) {
            BlockPos feet = base.above(dy);
            BlockPos head = feet.above();

            boolean feetEmpty = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
            boolean headEmpty = world.getBlockState(head).getCollisionShape(world, head).isEmpty();

            boolean feetSafeFluid = world.getFluidState(feet).isEmpty() || world.getFluidState(feet).is(FluidTags.WATER);
            boolean headSafeFluid = world.getFluidState(head).isEmpty() || world.getFluidState(head).is(FluidTags.WATER);

            if (!feetEmpty || !headEmpty || !feetSafeFluid || !headSafeFluid) continue;

            if (!allowAir) {
                BlockPos below = feet.below();
                boolean hasFloor = !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
                boolean floorSafeFluid = world.getFluidState(below).isEmpty() || world.getFluidState(below).is(FluidTags.WATER);
                if (!hasFloor || !floorSafeFluid) continue;
            }

            return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        }

        HitResult hit = world.clip(new ClipContext(
                player.getEyePosition(),
                desired,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 hitPos = hit.getLocation();
            Vec3 direction = desired.subtract(player.getEyePosition()).normalize();
            return hitPos.subtract(direction.scale(0.5));
        }

        return desired;
    }

    @Override
    public long getPrimaryCooldownMs() { return 0; }

    // =========================
    // SECONDARY
    // =========================

    @Override
    public void activateSecondary(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)) return;

        Entity target = getLookedAtEntity(player);

        if (!(target instanceof LivingEntity living)) {
            SecondaryWhiffFx(player, world);
            return;
        }

        Vec3 pPos = player.position();
        Vec3 tPos = target.position();

        player.teleportTo(world, tPos.x, tPos.y, tPos.z, java.util.Collections.emptySet(), player.getYRot(), player.getXRot());
        player.fallDistance = 0;

        target.teleportTo(pPos.x, pPos.y, pPos.z);
        faceEntity(player, living);

        world.playSound(null, player.blockPosition(), ModSounds.TELEPORTCLAP.get(), player.getSoundSource(), 0.7f, 1.4f);
        world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, player.getSoundSource(), 0.7f, 1.4f);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TeleportParticlePayload(
                TeleportParticlePayload.SWAP_HIT, pPos.x, pPos.y, pPos.z, tPos.x, tPos.y, tPos.z
        ));

        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 5, 100, true, false, false));
    }

    @Override
    public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }

    private static void SecondaryWhiffFx(ServerPlayer player, ServerLevel world) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(SECONDARY_RANGE));

        HitResult hr = world.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                player
        ));

        Vec3 hitPos = (hr.getType() == HitResult.Type.MISS) ? end : hr.getLocation();

        world.playSound(null, player.blockPosition(), ModSounds.TELEPORTCLAP.get(), player.getSoundSource(), 0.7f, 1.4f);
        world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, player.getSoundSource(), 0.7f, 1.4f);

        // CLIENT DISPATCH: Swap Whiff Beam
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TeleportParticlePayload(
                TeleportParticlePayload.SWAP_WHIFF, start.x, start.y, start.z, hitPos.x, hitPos.y, hitPos.z
        ));
    }

    // =========================
    // ULTIMATE
    // =========================

    @Override
    public void activateUltimate(ServerPlayer player) {
        TeleportState state = getState(player);
        state.frenzyTicks = ULTIMATE_DURATION_TICKS;
        state.frenzyStep = ULTIMATE_ATTACK_STEP_INITIAL;

        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, player.getSoundSource(), 0.8f, 1.4f);
    }

    @Override
    public long getUltimateCooldownMs() { return ULTIMATE_COOLDOWN_MS; }

    private void tickFrenzy(ServerPlayer player, TeleportState state) {
        // cancel if crouching
        if (player.isCrouching()) {
            state.frenzyTicks = 0;
            return;
        }

        state.frenzyTicks--;
        if (state.frenzyTicks <= 0) return;

        ServerLevel world = player.serverLevel();

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TeleportParticlePayload(
                TeleportParticlePayload.FRENZY_TICK, player.getX(), player.getY(), player.getZ(), 0, 0, 0
        ));

        if (state.frenzyStep > 0) {
            state.frenzyStep--;
            return;
        }

        state.frenzyStep = ULTIMATE_ATTACK_STEP_ONGOING;

        AABB box = new AABB(player.position(), player.position()).inflate(ULTIMATE_SEARCH_RADIUS);
        List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(RNG.nextInt(targets.size()));

        Vec3 behind = target.position().add(target.getViewVector(1.0f).scale(ULTIMATE_TELEPORT_OFFSET));
        Vec3 safePos = findSafeTeleportSpot(world, player, behind);

        BlockPos feetPos = BlockPos.containing(safePos);
        BlockPos headPos = feetPos.above();
        if (!world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty() ||
                !world.getBlockState(headPos).getCollisionShape(world, headPos).isEmpty()) {
            safePos = target.position();
        }

        safeTeleport(player, safePos.x, safePos.y, safePos.z);
        faceEntity(player, target);
        forceAttack(player, target);

        world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 0.6f, 1.5f);

        // CLIENT DISPATCH: Strike Effect
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TeleportParticlePayload(
                TeleportParticlePayload.FRENZY_STRIKE, player.getX(), player.getY(), player.getZ(), 0, 0, 0
        ));
    }

    // =========================
    // RAYCAST ENTITY HELPER
    // =========================

    private static Entity getLookedAtEntity(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = start.add(look.scale(SECONDARY_RANGE));

        AABB box = player.getBoundingBox().expandTowards(look.scale(SECONDARY_RANGE)).inflate(SECONDARY_HIT_MARGIN);

        var hit = ProjectileUtil.getEntityHitResult(
                player, start, end, box,
                e -> e instanceof LivingEntity && e != player,
                SECONDARY_RANGE * SECONDARY_RANGE
        );

        return hit != null ? hit.getEntity() : null;
    }

    // =========================
    // TELEPORT HELPERS
    // =========================

    private static void safeTeleport(ServerPlayer player, double x, double y, double z) {
        ServerLevel w = player.serverLevel();
        player.teleportTo(w, x, y, z, java.util.Collections.emptySet(), player.getYRot(), player.getXRot());
        player.fallDistance = 0;
    }

    private static void forceAttack(ServerPlayer player, LivingEntity target) {
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource frenzySrc = ModDamageTypes.frenzy(player.level(), player);
        player.swing(InteractionHand.MAIN_HAND, true);
        target.hurt(frenzySrc, baseDamage);
    }

    private static void faceEntity(ServerPlayer player, Entity target) {
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        Vec3 diff = targetPos.subtract(playerPos);
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float yaw = (float)(Mth.atan2(diff.z, diff.x) * (180F / Math.PI)) - 90F;
        float pitch = (float)(-(Mth.atan2(diff.y, horizontalDist) * (180F / Math.PI)));

        player.setYRot(yaw);
        player.setXRot(pitch);

        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }

    // names
    @Override public String getName() { return Component.translatable("power.loopypowers.teleport.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.teleport.primary.name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.teleport.secondary.name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.teleport.ultimate.name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.teleport.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.teleport.passive.name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.teleport.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.teleport.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.teleport.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.teleport.description.ultimate").getString();
    }
}