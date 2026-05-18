package com.loopy.loopypowers.power;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.network.payload.StormCloudPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.UUID;

public class LightningPower implements PowerInterface {
    private static final Random RNG = new Random();

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, LightningState> ACTIVE_STATES = new HashMap<>();

    private static class LightningState {
        int chargeTicks = 0;
        int chargeLockTicks = 0;
        boolean chargeReady = false;

        int superchargeTicks = 0;

        int stormTicks = 0;
        int stormStepTicks = 0;
    }

    private static LightningState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new LightningState());
    }

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleOptions BOLT_YELLOW =
            new DustParticleOptions(new Vector3f(1.00f, 0.88f, 0.05f), 1.4f);
    private static final DustParticleOptions BOLT_GOLD =
            new DustParticleOptions(new Vector3f(1.00f, 0.95f, 0.55f), 1.1f);
    private static final DustParticleOptions BOLT_WHITE =
            new DustParticleOptions(new Vector3f(1.00f, 1.00f, 0.82f), 1.6f);
    private static final DustParticleOptions STORM_BLACK =
            new DustParticleOptions(new Vector3f(0.08f, 0.08f, 0.10f), 1.8f);
    private static final DustParticleOptions STORM_GREY =
            new DustParticleOptions(new Vector3f(0.20f, 0.20f, 0.22f), 1.5f);

    // secondary active tag + tick interval for the aura
    private static final int    SUPERCHARGE_AURA_INTERVAL = 4; // aura pulse every 4 ticks (~0.2s)

    // PRIMARY
    private static final double CLAP_RANGE     = 7.0;
    private static final double CLAP_ANGLE_DEG = 65.0;  // horizontal spread
    private static final double CLAP_VERT_FLAT = 0.45;  // vertical squash on cone check (< 1 = flatter)

    private static final float  CLAP_MAX_DAMAGE    = 17.0f;   // close
    private static final float  CLAP_MIN_DAMAGE    = 4.5f;   // far
    // EGG Tuning
    private static final int PARTY_CHANCE = 650; // 1 in n chance

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("lt_")); // Cleanup legacy tags
        ACTIVE_STATES.put(player.getUUID(), new LightningState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("lt_"));
        ACTIVE_STATES.remove(player.getUUID());

        // Remove supercharge buffs
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.DIG_SPEED);
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        LightningState state = getState(player);

        // builds charge when not doing damage
        if (state.chargeLockTicks > 0) {
            state.chargeLockTicks--;
        }

        // max charge until no more increases
        if (state.chargeTicks < MAX_CHARGE_TICKS) {
            state.chargeTicks++;
        }
        int tier = getChargeTier(state.chargeTicks);

        // if player just reached tier 4
        if (tier >= 4 && !state.chargeReady) {
            state.chargeReady = true;

            ServerLevel w = player.serverLevel();

            // sound
            w.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT,
                    player.getSoundSource(), 0.8f, 1.6f);

            // bigger full-charge notification — yellow ring burst + sparks
            spawnChargeReadyBurst(w, player);

            // shake so the player actually notices they're fully charged
            CameraShake.shakeNearby(player, 5, 8, 0.18f);
        }

        // If charge leaves tier 4
        if (tier < 4 && state.chargeReady) {
            state.chargeReady = false;
        }

        // supercharge aura tick — emits particles while active
        if (state.superchargeTicks > 0) {
            state.superchargeTicks--;
            tickSuperchargeAura(player, state);
        }

        // Ult things --------------------
        if (state.stormTicks > 0) {
            tickMaelstrom(player, state);
        }
    }

    @Override
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        // Immunity to lightning damage
        return !source.is(DamageTypeTags.IS_LIGHTNING);
    }

    // PASSIVE - hahah static like lightning
    private static final int MAX_CHARGE_TICKS = 20 * 10;     // seconds to max charge (must meet 20 10 times)
    private static final float MAX_BONUS_DAMAGE = 5.0f;      // bonus damage for full charge
    private static final int MIN_PROC_TICKS = 20 * 2;        // must wait 2s without damaging to proc

    // ring burst when fully charged — much more noticeable than before
    private static void spawnChargeReadyBurst(ServerLevel world, ServerPlayer player) {
        Vec3 pos = player.position();

        // two concentric rings at ground + waist height
        for (double yOffset : new double[]{ 0.15, 1.0 }) {
            int points = 24;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                double speed = 0.18;
                world.sendParticles(BOLT_YELLOW,
                        pos.x + Math.cos(angle) * 0.4, pos.y + yOffset, pos.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
            }
        }
        // upward column of sparks
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50, 0.5, 0.8, 0.5, 0.10);
        world.sendParticles(BOLT_WHITE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                14, 0.4, 0.5, 0.4, 0.06);
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        // dodge if passive off
        if (!PassiveManager.isEnabled(attacker)) return; // this is for you dylan

        ServerLevel world = attacker.serverLevel();

        // these appear no matter the tier
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                6, 0.25, 0.35, 0.25, 0.02);
        world.sendParticles(BOLT_GOLD,
                target.getX(), target.getY() + 1.0, target.getZ(),
                4, 0.15, 0.20, 0.15, 0.015);
        world.playSound(null, target.blockPosition(),
                SoundEvents.REDSTONE_TORCH_BURNOUT, // sound
                attacker.getSoundSource(), 0.25f, 1.6f);

        LightningState state = getState(attacker);

        // tries to stop multi proc
        if (state.chargeLockTicks > 0) return;

        // This is the key: charge = time since last hit.
        if (state.chargeTicks < MIN_PROC_TICKS) {
            state.chargeTicks = 0;
            return;
        }

        // returns charge in tiers
        int tier = getChargeTier(state.chargeTicks);         // 1-4
        float bonus = computeBonusDamage(state.chargeTicks); // gets bonus damage

        // Bonus damage
        DamageSource src = ModDamageTypes.smite(attacker.level(), attacker);
        target.hurt(src, bonus);

        // Lightning based on tiers
        spawnTierLightning(world, target, tier);

        // yellow ring on target
        spawnHitRing(world, target, tier);

        int sparks = switch (tier) {
            case 1 -> 18;
            case 2 -> 28;
            case 3 -> 40;
            default -> 60;
        };

        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                sparks, 0.45, 0.7, 0.45, 0.08);
        world.sendParticles(BOLT_YELLOW,
                target.getX(), target.getY() + 1.0, target.getZ(),
                sparks / 2, 0.35, 0.55, 0.35, 0.07);

        world.playSound(null, target.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                attacker.getSoundSource(), 0.4f, 1.2f + (tier * 0.1f));

        world.playSound(null, target.blockPosition(),
                ModSounds.SHOCK.get(),
                attacker.getSoundSource(), 0.9f, 1.00f);

        // Stun
        if (tier >= 3) {
            target.addEffect(new MobEffectInstance(ModEffects.STUN, STUN_TICKS, STUN_AMP, true, true));
        }
        // chain stuff
        if (tier >= 4) {
            LivingEntity current = target;
            double chainRadius = 6.0; // reach
            int jumps = 3;            // how many times it can change

            for (int i = 0; i < jumps; i++) { // for each jump
                AABB box = current.getBoundingBox().inflate(chainRadius);

                LivingEntity currentTarget = current; // this is only here for some silly lambda rule

                List<LivingEntity> nearby = world.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        e -> e.isAlive() && e != attacker && e != currentTarget
                );

                if (nearby.isEmpty()) break;

                // pick nearest
                LivingEntity next = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : nearby) {
                    double d = e.distanceToSqr(current);
                    if (d < best) { best = d; next = e; }
                }
                if (next == null) break;

                next.hurt(ModDamageTypes.smite(attacker.level(), attacker), 2.0f);

                Vec3 a = current.position().add(0, current.getBbHeight() * 0.6, 0);
                Vec3 b = next.position().add(0, next.getBbHeight() * 0.6, 0);

                spawnChainTrail(world, a, b); // spawns chain effect

                world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        next.getX(), next.getY() + 1.0, next.getZ(),
                        14, 0.35, 0.5, 0.35, 0.06);
                world.sendParticles(BOLT_YELLOW,
                        next.getX(), next.getY() + 1.0, next.getZ(),
                        8, 0.25, 0.35, 0.25, 0.05);

                current = next;
            }
        }

        // resets charge
        state.chargeTicks = 0;

        // Small lock
        state.chargeLockTicks = 2;
    }

    // yellow ring that expands outward from the target on a charged hit
    private static void spawnHitRing(ServerLevel world, LivingEntity target, int tier) {
        int    points = 8 + tier * 4;    // more points at higher tier
        double speed  = 0.12 + tier * 0.04;
        double h      = target.getY() + 0.8;

        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points;
            DustParticleOptions col = (i % 2 == 0) ? BOLT_YELLOW : BOLT_WHITE;
            world.sendParticles(col,
                    target.getX() + Math.cos(angle) * 0.3,
                    h,
                    target.getZ() + Math.sin(angle) * 0.3,
                    1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
        }
    }

    private static int getChargeTier(int chargeTicks) {
        int c = Mth.clamp(chargeTicks, 0, MAX_CHARGE_TICKS);
        float t = (float) c / (float) MAX_CHARGE_TICKS;

        if (t >= 0.90f) return 4;
        if (t >= 0.65f) return 3;
        if (t >= 0.40f) return 2;
        return 1;
    }

    private static void spawnTierLightning(ServerLevel world, LivingEntity target, int tier) {
        // Tier 1-2 cosmetic bolt
        // Tier 3-4 real bolt
        boolean cosmetic = tier <= 2;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;

        bolt.moveTo(target.getX(), target.getY(), target.getZ());
        bolt.setVisualOnly(cosmetic);
        world.addFreshEntity(bolt);

        // second bolt if pretty much full charge
        if (tier >= 4 && RNG.nextFloat() < 0.35f) {
            LightningBolt bolt2 = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt2 != null) {
                bolt2.moveTo(target.getX() + (RNG.nextDouble() - 0.5) * 1.5,
                        target.getY(),
                        target.getZ() + (RNG.nextDouble() - 0.5) * 1.5);
                bolt2.setVisualOnly(true);
                world.addFreshEntity(bolt2);
            }
        }
    }

    // Stun target
    private static final int STUN_TICKS = 20;   // 1 second
    private static final int STUN_AMP   = 1;    // slowness/weakness amplifier

    private static void spawnChainTrail(ServerLevel world, Vec3 from, Vec3 to) { //blink code but new font
        Vec3 delta = to.subtract(from); // draws a line between two areas
        double len = delta.length();
        if (len < 0.01) return;

        int steps = Mth.clamp((int) (len * 10), 8, 60); // particles in line
        Vec3 step = delta.scale(1.0 / steps);

        Vec3 p = from;
        for (int i = 0; i <= steps; i++) {

            if (i % 3 == 0) {
                world.sendParticles(BOLT_YELLOW,
                        p.x, p.y, p.z, 1, 0.03, 0.08, 0.03, 0.0);
            } else {
                world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        p.x, p.y, p.z, 1, 0.04, 0.10, 0.8, 0.0);
            }
            p = p.add(step);
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();

        Vec3 origin  = player.getEyePosition();
        Vec3 forward = player.getViewVector(1.0f).normalize();

        // Squash vertically
        Vec3 flatForward = new Vec3(forward.x, forward.y * CLAP_VERT_FLAT, forward.z).normalize();
        double cos = Math.cos(Math.toRadians(CLAP_ANGLE_DEG));

        AABB box = new AABB(player.position(), player.position()).inflate(CLAP_RANGE, 2.5, CLAP_RANGE);
        List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        player.swing(InteractionHand.MAIN_HAND, true);

        // Roll for the funny
        boolean isParty = RNG.nextInt(PARTY_CHANCE) == 0;

        if (isParty) {
            world.playSound(null, player.blockPosition(), ModSounds.PARTYPOPPER.get(), player.getSoundSource(), 1.0f, 1.0f);
            spawnConfetti(world, player);
        } else {
            world.playSound(null, player.blockPosition(), ModSounds.THUNDERCLAP.get(), player.getSoundSource(), 0.7f, 1.4f);
            spawnClapCone(world, player);
        }

        for (LivingEntity target : targets) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
            double dist = to.length();

            if (dist < 0.001 || dist > CLAP_RANGE) continue;

            Vec3 flatTo = new Vec3(to.x, to.y * CLAP_VERT_FLAT, to.z).normalize();
            if (flatForward.dot(flatTo) < cos) continue;

            if (!player.hasLineOfSight(target)) continue;

            double t = 1.0 - (dist / CLAP_RANGE);
            target.hurt(ModDamageTypes.thunderclap(player.level(), player), (float) (CLAP_MIN_DAMAGE + t * (CLAP_MAX_DAMAGE - CLAP_MIN_DAMAGE)));

            // Hit feedback
            if (isParty) {
                spawnTargetConfetti(world, target, t);
            } else {
                world.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), (int)(8 + 20 * t), 0.4, 0.6, 0.4, 0.06);
                world.sendParticles(BOLT_YELLOW, target.getX(), target.getY() + 1.0, target.getZ(), (int)(6 + 12 * t), 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    private void spawnClapCone(ServerLevel world, ServerPlayer player) {
        Vec3 center  = player.position().add(0, 0.8, 0);   // at torso height
        Vec3 forward = player.getViewVector(1.0f).normalize();

        // right vector — spreads particles perpendicular to look direction
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();

        double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);

        // 6 depth slices along the forward axis; each slice is wider than the last
        int   depthSlices = 6;
        int   arcPoints   = 20;

        for (int d = 1; d <= depthSlices; d++) {
            double depth    = CLAP_RANGE * ((double) d / depthSlices);
            double arcWidth = depth * Math.tan(halfAngle);   // cone widens with depth
            Vec3  slicePos = center.add(forward.scale(depth));

            for (int i = 0; i <= arcPoints; i++) {
                // lateral offset within the arc width for this slice
                double lateral = -arcWidth + 2.0 * arcWidth * ((double) i / arcPoints);
                double jitter  = (RNG.nextDouble() - 0.5) * 0.6;   // break the grid

                double x = slicePos.x + right.x * (lateral + jitter);
                double z = slicePos.z + right.z * (lateral + jitter);
                // vertical: very tight — CLAP_VERT_FLAT squashes it
                double y = slicePos.y + (RNG.nextDouble() - 0.5) * 1.5;

                if (RNG.nextFloat() > 0.65f) continue;   // sparse

                DustParticleOptions col = d <= 2 ? BOLT_WHITE
                        : d <= 4 ? BOLT_YELLOW
                        : BOLT_GOLD;
                world.sendParticles(col, x, y, z, 1, 0, 0, 0, 0);

                if (RNG.nextFloat() < 0.20f) {
                    world.sendParticles(ParticleTypes.END_ROD,
                            x, y, z, 1,
                            forward.x * 0.12, 0.01, forward.z * 0.12, 0.0);
                }
            }
        }

        // small central column of sparks right at the player's hand — origin of the blast
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z,
                18, 0.3, 0.3, 0.3, 0.08);
        world.sendParticles(BOLT_WHITE,
                center.x, center.y, center.z,
                6, 0.15, 0.15, 0.15, 0.05);
    } // my head hurts

    // EGG
    private void spawnConfetti(ServerLevel world, ServerPlayer player) {
        Vec3 center  = player.position().add(0, 0.8, 0);
        Vec3 forward = player.getViewVector(1.0f).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();

        double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);
        int depthSlices = 6;
        int arcPoints = 20;

        for (int d = 1; d <= depthSlices; d++) {
            double depth = CLAP_RANGE * ((double) d / depthSlices);
            double arcWidth = depth * Math.tan(halfAngle);
            Vec3 slicePos = center.add(forward.scale(depth));

            for (int i = 0; i <= arcPoints; i++) {
                double lateral = -arcWidth + 2.0 * arcWidth * ((double) i / arcPoints);
                double jitter = (RNG.nextDouble() - 0.5) * 0.6;

                double x = slicePos.x + right.x * (lateral + jitter);
                double z = slicePos.z + right.z * (lateral + jitter);
                double y = slicePos.y + (RNG.nextDouble() - 0.5) * 1.5;

                if (RNG.nextFloat() > 0.50f) continue;

                // Randomized Rainbow Confetti
                Vector3f rainbow = new Vector3f(RNG.nextFloat(), RNG.nextFloat(), RNG.nextFloat());
                world.sendParticles(new DustParticleOptions(rainbow, 1.1f), x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnTargetConfetti(ServerLevel world, LivingEntity target, double t) {
        int count = (int)(25 + 30 * t);
        for (int i = 0; i < count; i++) {
            Vector3f color = new Vector3f(RNG.nextFloat(), RNG.nextFloat(), RNG.nextFloat());
            world.sendParticles(new DustParticleOptions(color, 0.85f), target.getX(), target.getY() + 1.0, target.getZ(), 1, 0.3, 0.4, 0.3, 0.03);
        }
    }

    private static final int SECONDARY_DURATION = 160;

    // SECONDARY
    @Override
    public void activateSecondary(ServerPlayer player) { // finally something simple
        ServerLevel world = player.serverLevel();

        //visual lightning
        spawnCosmeticLightning(world, player.position());

        // buffs
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,        SECONDARY_DURATION, 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,      SECONDARY_DURATION, 0, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,  SECONDARY_DURATION, 0, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,         SECONDARY_DURATION, 0, true, true));

        // start the per-tick aura — 150 ticks matches the buff duration
        LightningState state = getState(player);
        state.superchargeTicks = 150;

        spawnSuperchargeBurst(world, player);
    }

    private void tickSuperchargeAura(ServerPlayer player, LightningState state) {
        ServerLevel world = player.serverLevel();

        if (state.superchargeTicks % SUPERCHARGE_AURA_INTERVAL != 0) return;

        Vec3 pos = player.position();

        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points + (world.getGameTime() * 0.10);
            double r     = 0.65;
            world.sendParticles(BOLT_YELLOW,
                    pos.x + Math.cos(angle) * r, pos.y + 0.9, pos.z + Math.sin(angle) * r,
                    1, 0, 0.005, 0, 0.0);
        }

        if (RNG.nextFloat() < 0.5f) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + Math.cos(angle) * 0.65, pos.y + 0.9, pos.z + Math.sin(angle) * 0.65,
                    2, Math.cos(angle) * 0.10, 0.04, Math.sin(angle) * 0.10, 0.0);
        }

        if (state.superchargeTicks % (SUPERCHARGE_AURA_INTERVAL * 5) == 0) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + (RNG.nextDouble() - 0.5) * 0.4,
                    pos.y + 0.6 + RNG.nextDouble() * 1.2,
                    pos.z + (RNG.nextDouble() - 0.5) * 0.4,
                    1, Math.cos(angle) * 0.15, 0.06, Math.sin(angle) * 0.15, 0.0);
        }
    }

    private void spawnSuperchargeBurst(ServerLevel world, ServerPlayer player) {
        Vec3 center = player.position();

        // tight charge ring at the feet
        spawnChargeRing(world, player);

        // large expanding ring from player
        int    outerPoints = 32;
        double outerSpeed  = 0.30;
        for (int i = 0; i < outerPoints; i++) {
            double angle = i * Math.PI * 2.0 / outerPoints;
            DustParticleOptions col = (i % 3 == 0) ? BOLT_WHITE
                    : (i % 3 == 1) ? BOLT_YELLOW
                    : BOLT_GOLD;
            world.sendParticles(col,
                    center.x + Math.cos(angle) * 0.4, center.y + 0.3, center.z + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * outerSpeed, 0.01, Math.sin(angle) * outerSpeed, 0.0);
        }

        // dense particles on player
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 0.5, center.z, 80, 0.7, 1.2, 0.7, 0.12);
        world.sendParticles(BOLT_YELLOW,
                center.x, center.y + 1.0, center.z, 40, 0.5, 1.0, 0.5, 0.09);
        world.sendParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z, 16, 0.3, 0.8, 0.3, 0.07);

        // fx at player
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 2.0 / 8;
            world.sendParticles(ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * 0.4,
                    center.y + 0.8 + RNG.nextDouble(),
                    center.z + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.22, 0.04, Math.sin(angle) * 0.22, 0.0);
        }

        // cosmetic lightning
        for (int i = 0; i < 3; i++) {
            Vec3 offset = new Vec3(
                    center.x + (RNG.nextDouble() - 0.5) * 2.5,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 2.5);
            spawnCosmeticLightning(world, offset);
        }

        CameraShake.shakeNearby(player, 6, 10, 0.22f);

        world.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                player.getSoundSource(), 0.9f, 1.5f);
    }

    private void spawnChargeRing(ServerLevel world, ServerPlayer player) {
        Vec3 center = player.position();
        int points = 40;

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.x + Math.cos(angle) * 1.2;
            double z = center.z + Math.sin(angle) * 1.2;

            world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, center.y + 0.2, z,
                    1, 0, 0, 0, 0);
        }
    }

    // ULT
    private static final double STORM_RADIUS        = 15.0;
    private static final int    STORM_DURATION_TICKS = 300;  // 15 seconds
    private static final int    STORM_PULSE_TICKS    = 17;   // every 30 ticks

    private static final float STORM_DAMAGE     = 4.5f;


    @Override
    public void activateUltimate(ServerPlayer player) {
        LightningState state = getState(player);
        state.stormTicks = STORM_DURATION_TICKS;
        state.stormStepTicks = STORM_PULSE_TICKS;

        ServerLevel w = player.serverLevel();
        w.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                player.getSoundSource(), 1.2f, 0.8f);

        // Activation burst — spark/ring FX stay server-side; cloud reveal is client-side
        spawnMaelstromOpenBurst(w, player);

        // Send the activation cloud burst to all clients in range
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, new StormCloudPayload(player.getId(), STORM_DURATION_TICKS, true)
        );
    }

    // massive burst when ult activates
    private static void spawnMaelstromOpenBurst(ServerLevel world, ServerPlayer player) {
        Vec3 center = player.position();

        // four rings at ground, waist, chest, head
        for (double yOffset : new double[]{ 0.1, 0.6, 1.2, 1.9 }) {
            int    points = 28;
            double speed  = 0.35;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                DustParticleOptions col = switch (i % 3) {
                    case 0  -> BOLT_WHITE;
                    case 1  -> BOLT_YELLOW;
                    default -> BOLT_GOLD;
                };
                world.sendParticles(col,
                        center.x + Math.cos(angle) * 0.4, center.y + yOffset, center.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.015, Math.sin(angle) * speed, 0.0);
            }
        }

        // pillar of sparks and yellow
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 1.0, center.z, 100, 1.0, 1.5, 1.0, 0.12);
        world.sendParticles(BOLT_YELLOW,
                center.x, center.y + 1.0, center.z, 60, 0.8, 1.2, 0.8, 0.10);
        world.sendParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z, 20, 0.4, 0.8, 0.4, 0.07);

        // cosmetic lightning
        for (int i = 0; i < 5; i++) {
            Vec3 lPos = new Vec3(
                    center.x + (RNG.nextDouble() - 0.5) * 8.0,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 8.0);
            spawnCosmeticLightning(world, lPos);
        }

        CameraShake.shakeNearby(player, 12, 15, 0.38f);
    }

    private void tickMaelstrom(ServerPlayer player, LightningState state) {
        ServerLevel world = player.serverLevel();
        Vec3 center = player.position(); // follows player

        state.stormTicks--;
        if (state.stormTicks <= 0) {
            state.stormStepTicks = 0;
            return;
        }

        // Send cloud FX to all nearby clients every 6 ticks
        if (state.stormTicks % 6 == 0) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    player, new StormCloudPayload(player.getId(), state.stormTicks, false)
            );
        }

        // Targets inside radius
        AABB box = new AABB(center, center).inflate(STORM_RADIUS);
        List<LivingEntity> targets = world.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != player
        );

        // sparks on targets at risk of being hit
        if (!targets.isEmpty() && state.stormTicks % 3 == 0) {
            for (LivingEntity e : targets) {
                if (RNG.nextFloat() < 0.10f) {
                    world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            1, 0.25, 0.35, 0.25, 0.02);
                    world.sendParticles(BOLT_YELLOW,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            1, 0.15, 0.20, 0.15, 0.015);
                }
            }
        }

        // strike when timer done
        state.stormStepTicks--;

        if (state.stormStepTicks > 0) return;

        // reset timer
        state.stormStepTicks = STORM_PULSE_TICKS;

        if (targets.isEmpty()) return;

        // Strike 2 distinct targets if possible
        LivingEntity first = targets.get(RNG.nextInt(targets.size()));
        strikeStormTarget(world, player, first);

        if (targets.size() > 1) {
            LivingEntity second = first;
            for (int tries = 0; tries < 8 && second == first; tries++) {
                second = targets.get(RNG.nextInt(targets.size()));
            }
            if (second != first) strikeStormTarget(world, player, second);
        }
    }

    private void strikeStormTarget(ServerLevel world, ServerPlayer caster, LivingEntity target) {
        spawnCosmeticLightning(world, target.position());

        target.hurt(ModDamageTypes.smite(caster.level(), caster), STORM_DAMAGE);

        // fx
        spawnHitRing(world, target, 3);

        world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                22, 0.45, 0.7, 0.45, 0.08);
        world.sendParticles(BOLT_YELLOW,
                target.getX(), target.getY() + 1.0, target.getZ(),
                14, 0.35, 0.55, 0.35, 0.07);
        world.sendParticles(BOLT_WHITE,
                target.getX(), target.getY() + 0.8, target.getZ(),
                6, 0.20, 0.25, 0.20, 0.05);

        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI * 2.0 / 6;
            world.sendParticles(ParticleTypes.END_ROD,
                    target.getX() + Math.cos(angle) * 0.3,
                    target.getY() + 0.5 + RNG.nextDouble() * 1.5,
                    target.getZ() + Math.sin(angle) * 0.3,
                    1, Math.cos(angle) * 0.14, 0.06, Math.sin(angle) * 0.14, 0.0);
        }

        world.playSound(null, target.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                caster.getSoundSource(), 0.6f, 1.2f);

        // camerashake
        CameraShake.shakeNearby(caster, 10, 15, 0.25f);
    }

    // ===========================
    // HELPERS
    // ===========================

    private static float computeBonusDamage(int chargeTicks) {
        int clamped = Mth.clamp(chargeTicks, 0, MAX_CHARGE_TICKS);
        float t = (float) clamped / (float) MAX_CHARGE_TICKS; // 0..1

        // Ease curve: slow start, strong end
        float eased = t * t;

        return MAX_BONUS_DAMAGE * eased;
    }

    private static void spawnCosmeticLightning(ServerLevel world, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;

        bolt.moveTo(pos.x, pos.y, pos.z);
        bolt.setVisualOnly(true); // does no damage
        world.addFreshEntity(bolt);
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName()          { return Component.translatable("power.loopypowers.lightning.name").getString(); }
    @Override public String getPrimaryName()   { return Component.translatable("power.loopypowers.lightning.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.lightning.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Component.translatable("power.loopypowers.lightning.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 8_000;  } //
    @Override public long getSecondaryCooldownMs() { return 32_000; } //
    @Override public long getUltimateCooldownMs()  { return 450_000; } //


    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.lightning.description.overview").getString();
    }

    @Override public String getPassiveName() { return Component.translatable("power.loopypowers.lightning.passive_name").getString(); }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.lightning.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.lightning.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.lightning.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.lightning.description.ultimate").getString();
    }
}