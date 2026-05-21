package com.loopy.loopypowers.power;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.sound.ModSounds;
import com.loopy.loopypowers.ui.CooldownUI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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

        // primary state
        int gustCharges = GUST_MAX_CHARGES;
        int gustRechargeTicks = 0;
        int gustLockTicks = 0;
        int gustEmpowermentTicks = 0;

        // secondary state
        int updraftEmpowermentTicks = 0;
        int updraftBlocksBroken = 0;

        // Boom (Ultimate) State
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
    private static final int HURT_LOCK_DURATION = 40; // ground after hit
    private static final float HURT_KNOCKOUT_MIN_YVEL = -1.15f; // y level drop when hurt

    // Trail + sound cadence
    private static final int TRAIL_INTERVAL = 2;   // particle trail delay in flight
    private static final int SOUND_INTERVAL = 10;  // sound loop delay

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("fl_") && !tag.equals("fl_hecanfly_done"));

        ACTIVE_STATES.put(player.getUUID(), new FlightState());

        if (PassiveManager.isEnabled(player)) {
            equipWings(player);
        }

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
        if (player.getTags().contains("fl_hecanfly_done")) {
            DIED_WITH_EGG.add(player.getUUID());
        }
        onRemove(player);
    }

    private static void equipWings(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

        if (chest.is(com.loopy.loopypowers.item.ModItems.WINGS_OF_VALOR.get())) return;

        // Safely move current armor to inventory before equipping wings
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

        // ONLY unequip if they are actually wearing the wings (protects real armor)
        if (!chest.is(com.loopy.loopypowers.item.ModItems.WINGS_OF_VALOR.get())) return;

        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        FlightState state = getState(player);
        boolean passiveOn = PassiveManager.isEnabled(player);

        // EGG
        if (state.funnyTimer > 0) {
            state.funnyTimer--;
        } else {
            player.getTags().add("fl_hecanfly_done");
        }

        // timers
        if (state.stallTicks > 0) state.stallTicks--;
        if (state.gustLockTicks > 0) state.gustLockTicks--;

        // Gust recharge & UI updates (Always tick so they recharge while passive is off)
        tickGustRecharge(player, state);
        updateGustCooldownUI(player, state);

        if (passiveOn) {
            // enforce wings
            state.wingEnforceStep--;
            if (state.wingEnforceStep <= 0) {
                state.wingEnforceStep = 20;
                equipWings(player);
            }

            tickSonicBoomUltimate(player, state);
            tickPassiveFlight(player, state);
            tickGustEmpowerment(player, state);
            tickUpdraftEmpowerment(player, state);

            // particles while flying
            if (player.isFallFlying()) {
                tickFlightFx(player, state);
            } else {
                state.trailStep = 0;
                state.soundStep = 0;
            }

        } else {
            // Passive is OFF: Clean up states and unequip wings
            unequipWings(player);

            if (state.flightActive || player.isFallFlying()) {
                player.stopFallFlying();
                state.flightActive = false;
                state.trailStep = 0;
                state.soundStep = 0;
            }

            if (state.boomWindup > 0 || state.boomDash > 0) {
                clearBoomState(state);
                player.setDeltaMovement(player.getDeltaMovement().x, Math.min(player.getDeltaMovement().y, 0), player.getDeltaMovement().z);
                player.hurtMarked = true;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }

            if (state.gustEmpowermentTicks > 0) state.gustEmpowermentTicks--;
            if (state.updraftEmpowermentTicks > 0) state.updraftEmpowermentTicks--;
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

            // Sync knock-out velocity
            victim.hurtMarked = true;
            victim.connection.send(new ClientboundSetEntityMotionPacket(victim));

            // Apply grounded
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

    // PRIMARY: Gust
    private static final int GUST_MAX_CHARGES = 2;
    private static final int GUST_RECHARGE_TICKS = 160;
    private static final int GUST_LOCK_TICKS = 10;      // 0.5 sec delay between dashes

    private static final int GUST_EMPOWERMENT_DURATION = 50;
    private static final double GUST_BURST_STRENGTH = 1.8;
    private static final double GUST_EMPOWERMENT_PUSH = 0.07;
    private static final double GUST_MAX_HORIZ_SPEED = 3.3;

    @Override
    public boolean tryActivatePrimary(ServerPlayer player) {
        if (!PassiveManager.isEnabled(player)) {
            player.displayClientMessage(Component.translatable("message.loopypowers.passive_disabled").withStyle(ChatFormatting.RED), true);
            return false;
        }

        FlightState state = getState(player);

        if (state.gustLockTicks > 0) return false;
        if (state.gustCharges <= 0) return false;

        if (player.hasEffect(ModEffects.GROUNDED)) {
            player.displayClientMessage(Component.translatable("power.loopypowers.flight.grounded"), true);
            return false;
        }
        activatePrimary(player);
        return true;
    }

    @Override
    public void activatePrimary(ServerPlayer player) {
        FlightState state = getState(player);

        state.gustCharges--;
        state.gustLockTicks = GUST_LOCK_TICKS;

        if (state.gustRechargeTicks <= 0) {
            state.gustRechargeTicks = PowerManager.getModifiedCooldownTicks(player, GUST_RECHARGE_TICKS);
        }

        // force flight to start
        state.glideRequest = true;

        // FUNNY EGG
        if (player.isFallFlying() && !player.getTags().contains("fl_hecanfly_done")) {
            if (RNG.nextFloat() < 0.25f) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HECANFLY.get(), player.getSoundSource(), 1.2f, 1.0f);
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

        // Sync dash velocity
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        state.gustEmpowermentTicks = GUST_EMPOWERMENT_DURATION;

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

    private void tickGustRecharge(ServerPlayer player, FlightState state) {
        if (PowerManager.areCooldownsDisabled()) {
            state.gustCharges = GUST_MAX_CHARGES;
            state.gustRechargeTicks = 0;
            return;
        }

        if (state.gustCharges >= GUST_MAX_CHARGES) {
            state.gustRechargeTicks = 0;
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, GUST_RECHARGE_TICKS);

        if (state.gustRechargeTicks <= 0) {
            state.gustRechargeTicks = maxTicks;
        }

        state.gustRechargeTicks--;

        if (state.gustRechargeTicks <= 0) {
            state.gustCharges++;
            if (state.gustCharges < GUST_MAX_CHARGES) {
                state.gustRechargeTicks = maxTicks;
            } else {
                state.gustRechargeTicks = 0;
            }
        }
    }

    private void updateGustCooldownUI(ServerPlayer player, FlightState state) {
        String key = "FlightUI:PRIMARY";

        if (state.gustCharges >= GUST_MAX_CHARGES || PowerManager.areCooldownsDisabled()) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, GUST_RECHARGE_TICKS);
        long endMs = System.currentTimeMillis() + (state.gustRechargeTicks * 50L);

        Component suffix = CooldownUI.makeChargeSuffix(
                state.gustCharges, GUST_MAX_CHARGES, state.gustRechargeTicks, Math.max(1, maxTicks)
        );

        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
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

    // SECONDARY: Updraft (Homelander Takeoff & Burst)
    private static final double UPDRAFT_LAUNCH_Y = 3.2; // Reduced from 4.5 for more control
    private static final double UPDRAFT_KNOCKBACK_RADIUS = 5.0;
    private static final double UPDRAFT_KNOCKBACK_STRENGTH = 1.8;

    private static final int UPDRAFT_EMPOWERMENT_DURATION = 10; // Slightly shorter forced climb
    private static final double UPDRAFT_EMPOWERMENT_PUSH_Y = 0.25;
    private static final double UPDRAFT_MAX_Y_SPEED = 3.8; // Reduced from 5.5 to prevent extreme overshooting

    // Ceiling bursting mechanics
    private static final int UPDRAFT_MAX_BLOCKS_BROKEN = 20; // Increased capacity for the wider 9-point grid
    private static final float UPDRAFT_MAX_HARDNESS = 5.0f; // Breaks up to Iron block hardness. Obsi is 50.
    private static final float UPDRAFT_DAMAGE_PER_BLOCK = 1.0f;

    @Override
    public boolean tryActivateSecondary(ServerPlayer player) {
        if (!PassiveManager.isEnabled(player)) {
            player.displayClientMessage(Component.translatable("message.loopypowers.passive_disabled").withStyle(ChatFormatting.RED), true);
            return false;
        }

        if (player.hasEffect(ModEffects.GROUNDED)) {
            player.displayClientMessage(Component.translatable("power.loopypowers.flight.grounded"), true);
            return false;
        }
        activateSecondary(player);
        return true;
    }

    @Override
    public void activateSecondary(ServerPlayer player) {
        FlightState state = getState(player);
        ServerLevel w = player.serverLevel();

        state.updraftBlocksBroken = 0; // Reset broken block count

        // boom
        w.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY(), player.getZ(), 2, 0, 0, 0, 0);
        w.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 15, 1.5, 0.5, 1.5, 0.1);
        w.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 20, 2.0, 0.5, 2.0, 0.2);

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), player.getSoundSource(), 1.5f, 0.8f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.UPDRAFT.get(), player.getSoundSource(), 1.5f, 0.6f);

        CameraShake.shakeNearby(player, 15, 12, 1.2f);

        // kb
        AABB box = player.getBoundingBox().inflate(UPDRAFT_KNOCKBACK_RADIUS);
        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3 away = e.position().subtract(player.position());
            if (away.lengthSqr() < 0.0001) away = new Vec3(1, 0, 0);

            Vec3 kb = away.normalize().scale(UPDRAFT_KNOCKBACK_STRENGTH).add(0, 0.5, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(kb));
            e.hasImpulse = true;
            e.hurtMarked  = true; // sync velocity to all tracking clients, not just ServerPlayers

            if (e instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
        }

        // velocity
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.max(v.y, 0.0) + UPDRAFT_LAUNCH_Y, v.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // Force Flight & Empowerment
        player.startFallFlying();
        state.flightActive = true;
        state.updraftEmpowermentTicks = UPDRAFT_EMPOWERMENT_DURATION;
    }

    private void tickUpdraftEmpowerment(ServerPlayer player, FlightState state) {
        if (state.updraftEmpowermentTicks <= 0) return;
        state.updraftEmpowermentTicks--;

        // FIX: Re-enable flight if vanilla physics cancelled it by bumping the ceiling!
        if (!player.isFallFlying()) {
            player.startFallFlying();
        }

        ServerLevel w = player.serverLevel();
        Vec3 v = player.getDeltaMovement();
        int blocksBrokenNow = 0;

        // Multi-Point Raycast to reliably break blocks above the player's collision bounds.
        // We use 0.6 (slightly wider than the player's actual 0.3 half-width) to prevent
        // the player's shoulders from grazing unbreakable adjacent blocks.
        double hW = player.getBbWidth() * 0.6;

        // Expanded to a 9-point grid for a perfect 3x3 tunnel
        Vec3[] offsets = {
                new Vec3(0, 0, 0),         // Center
                new Vec3(hW, 0, hW),       // Corners
                new Vec3(-hW, 0, hW),
                new Vec3(hW, 0, -hW),
                new Vec3(-hW, 0, -hW),
                new Vec3(hW, 0, 0),        // Edges
                new Vec3(-hW, 0, 0),
                new Vec3(0, 0, hW),
                new Vec3(0, 0, -hW)
        };

        boolean hitUnbreakable = false;

        // Look slightly further ahead (3.5 blocks) to ensure we break the ceiling
        // before the physics engine calculates collision and halts momentum.
        double checkDist = Math.max(v.y, 3.5);

        for (Vec3 offset : offsets) {
            // Start raycast slightly inside the head to catch blocks already touching the player
            Vec3 start = player.position().add(offset).add(0, player.getBbHeight() - 0.2, 0);
            Vec3 end = start.add(0, checkDist, 0);

            int iterations = 0;
            // Raycast forward, breaking multiple blocks in a single line if necessary
            while (iterations < 6 && state.updraftBlocksBroken < UPDRAFT_MAX_BLOCKS_BROKEN) {
                iterations++;
                BlockHitResult hit = w.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = hit.getBlockPos();
                    BlockState bState = w.getBlockState(pos);
                    float hardness = bState.getDestroySpeed(w, pos);

                    if (hardness >= 0 && hardness <= UPDRAFT_MAX_HARDNESS) {
                        w.destroyBlock(pos, true, player);
                        state.updraftBlocksBroken++;
                        blocksBrokenNow++;

                        // Advance the ray start point past the broken block and cast again
                        start = hit.getLocation().add(0, 0.05, 0);
                    } else {
                        // Hit Bedrock / Obsidian
                        hitUnbreakable = true;
                        break;
                    }
                } else {
                    break; // Air
                }
            }
            if (hitUnbreakable) break;
        }

        if (hitUnbreakable) {
            state.updraftEmpowermentTicks = 0;
            player.setDeltaMovement(v.x, -0.2, v.z);
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            return;
        }

        // Hurt the player and shake camera if we smashed through blocks
        if (blocksBrokenNow > 0) {
            player.hurt(w.damageSources().flyIntoWall(), blocksBrokenNow * UPDRAFT_DAMAGE_PER_BLOCK);
            CameraShake.shakeNearby(player, 12, 10, 0.8f);
            w.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.2, 0.2, 0.2, 0.05);

            // Restore lost momentum from the physics engine collision so we keep bursting upwards!
            v = new Vec3(v.x, Math.max(v.y, UPDRAFT_LAUNCH_Y * 0.8), v.z);
        }

        double nextY = v.y + UPDRAFT_EMPOWERMENT_PUSH_Y;

        // Soft cap on upward speed
        if (nextY > UPDRAFT_MAX_Y_SPEED) {
            nextY = UPDRAFT_MAX_Y_SPEED;
        }

        player.setDeltaMovement(v.x, nextY, v.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        if (state.updraftEmpowermentTicks % 2 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() - 0.5, player.getZ(),
                    3, 0.2, 0.2, 0.2, 0.02);
        }
    }

    // Ultimate
    private static final int BOOM_WINDUP_TICKS = 30;
    private static final int BOOM_DASH_TICKS = 18;
    private static final double BOOM_SPEED = 5.0;
    private static final double BOOM_RADIUS = 4.0;
    private static final double BOOM_IMPACT_RADIUS = 6.0;
    private static final float  BOOM_IMPACT_DAMAGE = 18.5f;
    private static final double BOOM_IMPACT_KB = 2.8;
    private static final int BOOM_KNOCKOUT_DURATION = 60;

    @Override
    public boolean tryActivateUltimate(ServerPlayer player) {
        if (!PassiveManager.isEnabled(player)) {
            player.displayClientMessage(Component.translatable("message.loopypowers.passive_disabled").withStyle(ChatFormatting.RED), true);
            return false;
        }

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
                    e.hurtMarked  = true; // sync velocity to all tracking clients, not just ServerPlayers

                    // Sync knockback for pushed players
                    if (e instanceof ServerPlayer targetPlayer) {
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

    @Override public long getPrimaryCooldownMs() { return 0; } // Controlled completely by charges now
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
            e.hurtMarked  = true; // explicit sync; don't rely solely on hurt() setting this

            // kb sync
            if (e instanceof ServerPlayer targetPlayer) {
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