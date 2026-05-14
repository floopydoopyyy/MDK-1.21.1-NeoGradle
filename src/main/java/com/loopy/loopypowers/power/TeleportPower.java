package com.loopy.loopypowers.power;

import com.loopy.loopypowers.ui.CooldownUI;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import org.joml.Vector3f;

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
    private static final int ULTIMATE_ATTACK_STEP_INITIAL = 4;    // startup delay before first swing
    private static final int ULTIMATE_ATTACK_STEP_ONGOING = 6;    // ticks between each swing
    private static final double ULTIMATE_SEARCH_RADIUS = 7.0;     // how far to look for targets
    private static final double ULTIMATE_TELEPORT_OFFSET = -1.5;  // how far behind victim to teleport
    private static final double ULTIMATE_AURA_RADIUS = 11.0;      // visual ring radius

    // Ultimate Custom Particles
    private static final DustParticleOptions FRENZY_DUST = new DustParticleOptions(new Vector3f(0.55f, 0.0f, 0.85f), 1.5f); // Deep purple
    private static final DustParticleOptions FRENZY_RING = new DustParticleOptions(new Vector3f(0.85f, 0.2f, 1.0f), 1.2f);  // Bright magenta

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
        tickBlinkRecharge(state);
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

    /**
     * Return true if we dodged (meaning: cancel the damage).
     */
    public boolean tryDodge(ServerPlayer player) {
        // do not dodge if passive off
        if (!PassiveManager.isEnabled(player)) return false;

        TeleportState state = getState(player);

        // leave if cooldown is active
        if (state.dodgeCdTicks > 0) return false;

        if (RNG.nextFloat() > PASSIVE_DODGE_CHANCE) return false; // dice roll

        // request packet to hide player
        RenderPackets.hidePlayerFromOthers(player, PASSIVE_HIDE_TICKS);

        // window of invincibility (otherwise it would just hit again)
        state.phaseTicks = PASSIVE_IFRAME_TICKS;

        // cooldown setting
        state.dodgeCdTicks = PASSIVE_COOLDOWN_TICKS;

        // particles
        var w = player.serverLevel();
        w.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY()+1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.08);
        // player is removed from people's client to go invisible
        w.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, player.getSoundSource(), 1.0f, 1.2f);

        return true; // tells server to cancel damage
    }

    // =========================
    // PRIMARY
    // =========================

    @Override
    public void activatePrimary(ServerPlayer player) {
        TeleportState state = getState(player);
        if (state.blinkLockTicks > 0) return;
        if (state.blinkCharges <= 0) return;

        // consume charge
        state.blinkCharges--;
        state.blinkLockTicks = PRIMARY_LOCK_TICKS;

        // start recharge if not running
        if (state.blinkRechargeTicks < 0) {
            state.blinkRechargeTicks = PRIMARY_RECHARGE_TICKS;
        }

        blinkForward(player);
    }

    private static void tickBlinkRecharge(TeleportState state) {
        // if full, exit
        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            state.blinkRechargeTicks = -1;
            return;
        }

        // tick timer
        if (state.blinkRechargeTicks >= 0) {
            state.blinkRechargeTicks--;

            // when hit 0, give a charge
            if (state.blinkRechargeTicks <= 0) {
                state.blinkCharges++;

                // If still not full, start next recharge window
                if (state.blinkCharges < PRIMARY_MAX_CHARGES) {
                    state.blinkRechargeTicks = PRIMARY_RECHARGE_TICKS;
                } else {
                    state.blinkRechargeTicks = -1;
                }
            }
        }
    }

    private static void updateBlinkCooldownUI(ServerPlayer player, TeleportState state) {
        String key = "Teleport:PRIMARY";

        // hide ui when at full charge
        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        // show progress towards nearest charge
        int leftTicks = state.blinkRechargeTicks;
        if (leftTicks < 0) leftTicks = PRIMARY_RECHARGE_TICKS;

        long endMs = System.currentTimeMillis() + (leftTicks * 50L);

        String suffix = makeChargeSuffix(
                state.blinkCharges, PRIMARY_MAX_CHARGES, leftTicks, PRIMARY_RECHARGE_TICKS
        ).getString();

        setCooldownEnd(player, key, endMs, Component.literal(suffix));
    }

    private static void blinkForward(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);

        // Scale distance based on pitch (looking down reduces distance)
        // look.y ranges from 1.0 (straight up) to -1.0 (straight down)
        double distanceMultiplier = 1.0;
        if (look.y < 0) {
            // If looking down, scale distance linearly (e.g., -1.0 pitch = 20% distance)
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

        // keep them grounded if possible
        if (!allowAir) {
            desired = new Vec3(desired.x, player.getY(), desired.z);
        }

        Vec3 safe = findSafeTeleportSpot(world, player, desired);

        Vec3 origin = player.position();

        // Trail
        spawnBlinkTrail(world, origin, safe);

        // Origin
        world.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1, origin.z, 30, 0.4, 0.7, 0.4, 0.1);
        world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 1.0f, 1.2f);

        // Sound
        world.playSound(null, player.blockPosition(),
                ModSounds.TELEPORTSNAP.get(),
                player.getSoundSource(),
                0.7f, 1.4f);

        // Teleport
        safeTeleport(player, safe.x, safe.y, safe.z);

        // Destination
        world.sendParticles(ParticleTypes.PORTAL, safe.x, safe.y + 1, safe.z, 30, 0.4, 0.7, 0.4, 0.1);

        // briefly give haste to refresh attack cooldown instantly
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SPEED, 5, 50, true, false, false
        ));
    }

    private static Vec3 findSafeTeleportSpot(ServerLevel world, ServerPlayer player, Vec3 desired) {
        BlockPos base = BlockPos.containing(desired.x, desired.y, desired.z);

        // If looking up enough, allow air placement
        Vec3 look = player.getViewVector(1.0f);
        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;

        // Try Y-offsets to find a safe spot
        int[] order = new int[] { 0, 1, -1, 2, -2, 3, -3 };

        for (int dy : order) {
            BlockPos feet = base.above(dy);
            BlockPos head = feet.above();

            // Check if space is physically empty (no blocks)
            boolean feetEmpty = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
            boolean headEmpty = world.getBlockState(head).getCollisionShape(world, head).isEmpty();

            // Check for dangerous fluids (lava)
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

        // If no completely safe spot found near desired, try raycasting to find the last safe spot *before* hitting a wall
        HitResult hit = world.clip(new ClipContext(
                player.getEyePosition(),
                desired,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            // Step back slightly from the wall
            Vec3 hitPos = hit.getLocation();
            Vec3 direction = desired.subtract(player.getEyePosition()).normalize();
            return hitPos.subtract(direction.scale(0.5));
        }

        return desired;
    }

    @Override
    public long getPrimaryCooldownMs() { return 0; } // Handled by charge system

    // =========================
    // SECONDARY
    // =========================

    @Override
    public void activateSecondary(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)) return;

        Entity target = getLookedAtEntity(player);

        // if whiff do a thing
        if (!(target instanceof LivingEntity living)) {
            SecondaryWhiffFx(player, world);
            return;
        }

        // spawn beam to show where aimed
        Vec3 beamStart = player.getEyePosition();
        Vec3 beamEnd = living.position().add(0, living.getBbHeight() * 0.5, 0); // mid-body looks nice
        spawnAimBeam(world, beamStart, beamEnd);
        world.sendParticles(ParticleTypes.PORTAL, beamEnd.x, beamEnd.y, beamEnd.z, 18, 0.25, 0.25, 0.25, 0.06);

        Vec3 pPos = player.position();
        Vec3 tPos = target.position();

        // Swap directly to coordinates without safety checks
        player.teleportTo(world, tPos.x, tPos.y, tPos.z, java.util.Collections.emptySet(), player.getYRot(), player.getXRot());
        player.fallDistance = 0;

        target.teleportTo(pPos.x, pPos.y, pPos.z);
        faceEntity(player, living);

        // particles
        spawnBlinkTrail(world, pPos, tPos); // player -> target
        spawnBlinkTrail(world, tPos, pPos); // target -> player

        world.sendParticles(ParticleTypes.PORTAL, pPos.x, pPos.y+1, pPos.z, 50, 0.5, 0.8, 0.5, 0.1);
        world.sendParticles(ParticleTypes.PORTAL, tPos.x, tPos.y+1, tPos.z, 50, 0.5, 0.8, 0.5, 0.1);

        world.playSound(null, player.blockPosition(), ModSounds.TELEPORTCLAP.get(), player.getSoundSource(), 0.7f, 1.4f);
        world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, player.getSoundSource(), 0.7f, 1.4f);

        // Instantly refresh attack cooldown
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SPEED, 5, 50, true, false, false
        ));
    }

    @Override
    public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }

    private static void SecondaryWhiffFx(ServerPlayer player, ServerLevel world) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(SECONDARY_RANGE));

        HitResult hr = world.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 hitPos = (hr.getType() == HitResult.Type.MISS) ? end : hr.getLocation();

        // beam
        spawnAimBeam(world, start, hitPos);
        world.sendParticles(ParticleTypes.PORTAL, hitPos.x, hitPos.y, hitPos.z, 18, 0.25, 0.25, 0.25, 0.06);

        // sound
        world.playSound(null, player.blockPosition(),
                ModSounds.TELEPORTCLAP.get(),
                player.getSoundSource(),
                0.7f, 1.4f);
        world.playSound(null, player.blockPosition(),
                SoundEvents.CHORUS_FRUIT_TELEPORT,
                player.getSoundSource(),
                0.7f, 1.4f);
    }

    private static void spawnAimBeam(ServerLevel world, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = Mth.clamp((int)(len * 18), 10, 140);
        Vec3 step = delta.scale(1.0 / steps);

        Vec3 p = from;
        for (int i = 0; i <= steps; i++) {
            world.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    p.x, p.y, p.z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0
            );
            p = p.add(step);
        }
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
        state.frenzyTicks--;
        if (state.frenzyTicks <= 0) return;

        ServerLevel world = player.serverLevel();
        spawnFrenzyRadius(world, player);
        spawnFrenzyAura(world, player);

        if (state.frenzyStep > 0) {
            state.frenzyStep--;
            return;
        }

        state.frenzyStep = ULTIMATE_ATTACK_STEP_ONGOING;

        AABB box = new AABB(player.position(), player.position()).inflate(ULTIMATE_SEARCH_RADIUS);
        List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(RNG.nextInt(targets.size()));

        // calculate desired spot behind target
        Vec3 behind = target.position().add(target.getViewVector(1.0f).scale(ULTIMATE_TELEPORT_OFFSET));
        // force the Y coordinate to match the target's feet to prevent hovering if target is on a slope

        // use the safe spot finder
        Vec3 safePos = findSafeTeleportSpot(world, player, behind);

        // Safety Fallback: If the safe spot finder returned a location that is still suffocating
        // just teleport to the target's exact position.
        BlockPos feetPos = BlockPos.containing(safePos);
        BlockPos headPos = feetPos.above();
        if (!world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty() ||
                !world.getBlockState(headPos).getCollisionShape(world, headPos).isEmpty()) {
            safePos = target.position();
        }

        safeTeleport(player, safePos.x, safePos.y, safePos.z);
        faceEntity(player, target);

        forceAttack(player, target);

        // strike particles
        world.sendParticles(FRENZY_DUST, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.08);
        world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 0.6f, 1.5f);
    }

    private static void spawnFrenzyRadius(ServerLevel world, ServerPlayer player) {
        Vec3 center = player.position();

        int points = 80; // more points makes the circle more circly
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.x + Math.cos(angle) * ULTIMATE_AURA_RADIUS;
            double z = center.z + Math.sin(angle) * ULTIMATE_AURA_RADIUS;

            world.sendParticles(
                    FRENZY_RING,
                    x,
                    center.y + 0.1,
                    z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0
            );
        }
    }

    private static void spawnFrenzyAura(ServerLevel world, ServerPlayer player) {
        Vec3 pos = player.position();

        world.sendParticles(
                FRENZY_DUST,
                pos.x,
                pos.y + 1.0,
                pos.z,
                3,
                0.6,
                1.0,
                0.6,
                0.02
        );

        world.sendParticles(
                ParticleTypes.REVERSE_PORTAL, //
                pos.x,
                pos.y + 0.5,
                pos.z,
                20,
                0.3,
                0.6,
                0.3,
                0.01
        );
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
                player,
                start,
                end,
                box,
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

    // =========================
    // HELPER METHODS
    // =========================

    private static void forceAttack(ServerPlayer player, LivingEntity target) {
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource frenzySrc = ModDamageTypes.frenzy(player.level(), player);

        float totalDamage = baseDamage;
        // Simplified damage logic due to Mojmap 1.21.1 EnchantmentHelper complexities

        player.swing(InteractionHand.MAIN_HAND, true);
        target.hurt(frenzySrc, totalDamage);
    }

    private static void faceEntity(ServerPlayer player, Entity target) { // makes player face entity
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        Vec3 diff = targetPos.subtract(playerPos);

        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float pitch = (float)(-(Mth.atan2(dy, horizontalDist) * (180F / Math.PI)));

        player.setYRot(yaw);
        player.setXRot(pitch);

        player.connection.teleport(
                player.getX(),
                player.getY(),
                player.getZ(),
                yaw,
                pitch
        );
    }

    private static void spawnBlinkTrail(ServerLevel world, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from); // i would try to explain this but even i don't know. I was looking at a forum
        double len = delta.length();
        if (len < 0.001) return;

        int steps = Mth.clamp((int)(len * 12), 8, 80); // len is how many particles between origin and destination
        Vec3 step = delta.scale(1.0 / steps);

        Vec3 p = from;
        for (int i = 0; i <= steps; i++) { // particle details; heh... this is easy..!
            world.sendParticles(
                    ParticleTypes.PORTAL,
                    p.x, p.y + 1.0, p.z,
                    1,            // count per step
                    0.02, 0.15, 0.02,  // spread
                    0.0
            );
            p = p.add(step);
        }
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