package com.loopy.loopypowers.power;

import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.ExplosionDropZonePayload;
import com.loopy.loopypowers.sound.ModSounds;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.ui.CooldownUI;
import com.loopy.loopypowers.manager.PowerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExplosionPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, ExplosionState> ACTIVE_STATES = new HashMap<>();
    private boolean applyingReducedDamage = false;

    private static class ExplosionState {
        int ignitingTicks = 0;

        int blastCharges = BLAST_MAX_CHARGES;
        int blastRechargeTicks = 0;
        int blastLockTicks = 0;

        int noSelfExpTicks = 0;

        int ultActiveTicks = 0;
        int ultWarnTicks = -1;
        int ultAirTicks = 0;
        int ultStage = 0;
        boolean ultWaitingLand = false;

        int ultLaunchDelayTicks = 0;
        double launchX = 0;
        double launchY = 0;
        double launchZ = 0;
    }

    private static ExplosionState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new ExplosionState());
    }

    /* ============================================================
       TUNING
       ============================================================ */

    // PASSIVE
    private static final float FALL_DAMAGE_MULT = 0.7f;

    // Primary: ignition
    private static final int IGNITE_FUSE_TICKS = 20 * 5;
    private static final float IGNITE_POWER = 4.5f;
    private static final int IGNITE_SPEED_AMP = 1;
    private static final int IGNITE_JUMP_AMP = 2; // Jump Boost Modifier
    private static final boolean IGNITE_BREAK_BLOCKS = true;

    // Secondary: blast
    private static final int BLAST_MAX_CHARGES = 2;
    private static final int BLAST_RECHARGE_TICKS = 220;
    private static final int BLAST_LOCK_TICKS = 4;
    private static final float BLAST_POWER = 2.6f;
    private static final boolean BLAST_BREAK_BLOCKS = true;
    private static final double BLAST_SPAWN_DIST = 1.2;
    private static final double BLAST_SPAWN_DOWN = 0.10;

    // Recoil movement tuning
    private static final double RECOIL_STRENGTH = 0.85;
    private static final double RECOIL_UP_BONUS = 0.35;
    private static final double RECOIL_MAX_Y = 2.20;
    private static final double RECOIL_MAX_H = 2.0;

    // Ultimate: Chain Reaction (EXPOSED FOR CLIENT)
    public static final int ULT_POPS_ACTUAL = 3;

    private static final int ULT_WARN_TICKS = 8;
    private static final int ULT_FIZZLE_AIR_TICKS = 500;

    private static final double ULT_AIR_STEER = 0.18;
    private static final double ULT_POP_LAUNCH_Y = 2.70;
    private static final double ULT_FINAL_LAUNCH_Y = 1.10;

    private static final int ULT_TOTAL_TICKS = 1000;
    private static final float ULT_POP_POWER = 3.2f;
    private static final boolean ULT_POP_BREAK_BLOCKS = true;
    private static final float ULT_FINAL_POWER = 8.4f;
    private static final boolean ULT_FINAL_BREAK_BLOCKS = true;
    private static final int ULT_CHARGE_SLOWNESS_AMP = 4;
    private static final int ULT_CHARGE_REFRESH_TICKS = 10;

    // DAMAGE AND RADII (EXPOSED FOR CLIENT)
    private static final float IGNITE_DAMAGE = 23.5f;
    private static final double IGNITE_DMG_RADIUS = 5.5;

    private static final float BLAST_DAMAGE = 21.0f;
    private static final double BLAST_DMG_RADIUS = 3.0;

    private static final float ULT_POP_DAMAGE = 22.5f;
    public static final double ULT_POP_DMG_RADIUS = 4.5;
    private static final float ULT_FINAL_DAMAGE = 25.0f;
    public static final double ULT_FINAL_DMG_RADIUS = 6.5;

    private static final int ULT_LAUNCH_DELAY_TICKS = 2;
    private static final double ULT_LAUNCH_KICK_Y = 0.12;

    // funnies
    private static final float GLASS_CONVERT_CHANCE = 0.07f;
    private static final float WHY_SOUND_CHANCE = 0.005f;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("ex_"));
        ACTIVE_STATES.put(player.getUUID(), new ExplosionState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("ex_"));
        ACTIVE_STATES.remove(player.getUUID());

        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(ModEffects.BRACED);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ExplosionDropZonePayload(player.getId(), false, 0));
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        ExplosionState state = getState(player);

        if (state.blastLockTicks > 0) state.blastLockTicks--;
        if (state.noSelfExpTicks > 0) state.noSelfExpTicks--;

        tickBlastRecharge(player, state);
        updateBlastCooldownUI(player, state);

        if (state.ignitingTicks > 0) {
            tickIgnition(player, state);
        }

        if (state.ultActiveTicks > 0) {
            tickUltimate(player, state);
        }

        if (state.ultLaunchDelayTicks > 0) {
            tickPendingLaunch(player, state);
        }
    }

    @Override
    public boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        if (this.applyingReducedDamage) return true;

        ExplosionState state = getState(victim);

        // reduce fall damage
        if (source.is(DamageTypes.FALL)) {
            this.applyingReducedDamage = true;
            victim.hurt(source, amount * FALL_DAMAGE_MULT);
            this.applyingReducedDamage = false;

            return false; // Intercept and cancel the original unmitigated damage
        }

        if (!victim.onGround() && state.ultActiveTicks > 0) {
            if (source.getEntity() instanceof LivingEntity && amount >= 3.0f) {
                if (victim.level() instanceof ServerLevel sw) {
                    forceEarlyDetonation(victim, state, sw);
                }
            }
        }

        return state.noSelfExpTicks <= 0 || !source.is(DamageTypeTags.IS_EXPLOSION);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ExplosionState state = getState(player);
        state.ignitingTicks = IGNITE_FUSE_TICKS;

        ServerLevel w = player.serverLevel();
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TNT_PRIMED,
                player.getSoundSource(),
                1.0f, 1.0f);

        w.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.35, 0.35, 0.35, 0.02);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickIgnition(ServerPlayer player, ExplosionState state) {
        state.ignitingTicks--;
        ServerLevel w = player.serverLevel();

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, IGNITE_SPEED_AMP, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 10, IGNITE_JUMP_AMP, true, false));

        float progress = 1.0f - (state.ignitingTicks / (float) IGNITE_FUSE_TICKS);
        progress = Mth.clamp(progress, 0.0f, 1.0f);

        int interval = Mth.clamp((int) Mth.lerp(progress, 6.0f, 1.0f), 1, 6);

        if (w.getGameTime() % interval == 0) {
            int smokeCount = 2 + (int)(progress * 10.0f);
            w.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 0.9, player.getZ(),
                    smokeCount,
                    0.25, 0.25, 0.25,
                    0.01);

            if (progress > 0.55f) {
                int flameCount = 1 + (int)((progress - 0.55f) * 10.0f);
                w.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.9, player.getZ(),
                        flameCount,
                        0.18, 0.22, 0.18,
                        0.005);
            }

            if (state.ignitingTicks <= 30) {
                w.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        2,
                        0.20, 0.25, 0.20,
                        0.01);
            }
        }

        if (w.getGameTime() % 20 == 0) {
            float pitch = 0.9f + 0.35f * progress;
            w.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TNT_PRIMED,
                    player.getSoundSource(),
                    0.55f,
                    pitch);
        }

        if (state.ignitingTicks > 0) return;

        state.noSelfExpTicks = 6;

        Vec3 center = player.position().add(0, 0.1, 0);

        explodeAt(w, center, IGNITE_POWER, IGNITE_BREAK_BLOCKS);
        applyExplosionDamage(player, w, center, IGNITE_DMG_RADIUS, IGNITE_DAMAGE, false, false);

        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.max(v.y, 0.65), v.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.fallDistance = 0.0f;

        CameraShake.shakeNearby(player, 9.0, 16, 1.4f);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ExplosionState state = getState(player);
        if (state.blastLockTicks > 0) return;
        if (state.blastCharges <= 0) return;

        ServerLevel w = player.serverLevel();

        // blast
        state.blastCharges--;
        state.blastLockTicks = BLAST_LOCK_TICKS;

        // Start dynamic recharge timer using PowerManager
        if (state.blastRechargeTicks <= 0) {
            state.blastRechargeTicks = PowerManager.getModifiedCooldownTicks(player, BLAST_RECHARGE_TICKS);
        }

        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 origin = player.getEyePosition()
                .add(look.scale(BLAST_SPAWN_DIST))
                .add(0.0, -BLAST_SPAWN_DOWN, 0.0);

        state.noSelfExpTicks = 6;

        explodeAt(w, origin, BLAST_POWER, BLAST_BREAK_BLOCKS);
        applyExplosionDamage(player, w, origin, BLAST_DMG_RADIUS, BLAST_DAMAGE, false, true);

        applyRecoil(player, origin);

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(),
                0.9f, 1.15f);

        CameraShake.shakeNearby(player, 8.0, 8, 0.95f);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickBlastRecharge(ServerPlayer player, ExplosionState state) {
        // instant full recharge if disabled via toggle
        if (PowerManager.areCooldownsDisabled()) {
            state.blastCharges = BLAST_MAX_CHARGES;
            state.blastRechargeTicks = 0;
            return;
        }

        if (state.blastCharges >= BLAST_MAX_CHARGES) {
            state.blastRechargeTicks = 0;
            return;
        }

        // fetch the max ticks based on player stats/multipliers
        int maxTicks = PowerManager.getModifiedCooldownTicks(player, BLAST_RECHARGE_TICKS);

        if (state.blastRechargeTicks <= 0) {
            state.blastRechargeTicks = maxTicks;
        }

        state.blastRechargeTicks--;

        // 3. Use <= 0 to safely handle 0.0x multipliers
        if (state.blastRechargeTicks <= 0) {
            state.blastCharges++;
            if (state.blastCharges < BLAST_MAX_CHARGES) {
                state.blastRechargeTicks = maxTicks;
            } else {
                state.blastRechargeTicks = 0;
            }
        }
    }

    private static void applyRecoil(ServerPlayer player, Vec3 explosionOrigin) {
        Vec3 look = player.getViewVector(1.0f);

        // pPush in the opposite direction of where the player is looking
        Vec3 recoilDir = look.scale(-1.0);

        // Decoupled horizontal scaling to make horizontal mobility much snappier
        double pushX = recoilDir.x * RECOIL_STRENGTH * 2.0;
        double pushZ = recoilDir.z * RECOIL_STRENGTH * 2.0;

        // Stronger vertical scaling based directly on looking up/down
        double pushY = recoilDir.y * RECOIL_STRENGTH * 1.3;

        // Base upward boost so sideways jumps still grant a small hop
        pushY += RECOIL_UP_BONUS;

        Vec3 v = player.getDeltaMovement();
        double nx = v.x + pushX;
        double nz = v.z + pushZ;

        double ny;
        if (recoilDir.y < -0.1) {
            // Player is looking up, they want to slam downwards
            ny = v.y + pushY;
        } else {
            // Player is looking down/straight, pop them upwards and cancel their fall
            ny = Math.max(v.y, 0.0) + pushY;
        }

        // Cap horizontal speed to maintain control
        Vec3 hv = new Vec3(nx, 0.0, nz);
        double hLen = hv.length();
        if (hLen > RECOIL_MAX_H) {
            Vec3 hN = hv.normalize().scale(RECOIL_MAX_H);
            nx = hN.x;
            nz = hN.z;
        }

        // Cap max UPWARD speed, but let them slam downwards normally
        if (ny > RECOIL_MAX_Y) {
            ny = RECOIL_MAX_Y;
        }

        player.setDeltaMovement(nx, ny, nz);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.fallDistance = 0.0f;
    }

    private static void updateBlastCooldownUI(ServerPlayer player, ExplosionState state) {
        String key = "ExplosionUI:SECONDARY";

        if (state.blastCharges >= BLAST_MAX_CHARGES || PowerManager.areCooldownsDisabled()) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        // get time
        int maxTicks = PowerManager.getModifiedCooldownTicks(player, BLAST_RECHARGE_TICKS);
        long endMs = System.currentTimeMillis() + (state.blastRechargeTicks * 50L);

        Component suffix = CooldownUI.makeChargeSuffix(
                state.blastCharges, BLAST_MAX_CHARGES, state.blastRechargeTicks, Math.max(1, maxTicks)
        );

        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        ExplosionState state = getState(player);

        state.ultActiveTicks = ULT_TOTAL_TICKS;
        state.ultAirTicks = ULT_FIZZLE_AIR_TICKS;
        state.ultStage = 0;
        state.ultWarnTicks = -1;

        if (!player.onGround()) {
            state.ultWaitingLand = true;
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ExplosionDropZonePayload(player.getId(), true, 0));

        ServerLevel w = player.serverLevel();
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TNT_PRIMED,
                player.getSoundSource(),
                1.0f, 0.8f);
        CameraShake.shakeNearby(player, 10.0, 12, 1.1f);
    }

    private static void tickUltimate(ServerPlayer player, ExplosionState state) {
        ServerLevel w = player.serverLevel();
        state.ultActiveTicks--;

        tickUltAmbientFx(player, w);

        if (!player.onGround()) {
            if (state.ultAirTicks > 0) {
                state.ultAirTicks--;
                if (state.ultAirTicks == 0) {
                    cancelUltimate(player, state, w);
                }
            }
            return;
        }

        if (state.ultWaitingLand) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ExplosionDropZonePayload(player.getId(), false, state.ultStage));
        }

        state.ultAirTicks = ULT_FIZZLE_AIR_TICKS;
        state.ultWaitingLand = false;

        if (state.ultWarnTicks >= 0) {
            tickUltFinisherChargeFx(player, w, state.ultWarnTicks);

            if (state.ultWarnTicks == 0) {
                state.ultWarnTicks = -1;
                state.ultActiveTicks = 0;
                doUltPop(player, state, true, 3);
            } else {
                state.ultWarnTicks--;
            }
            return;
        }

        if (state.ultStage < ULT_POPS_ACTUAL) {
            doUltPop(player, state, false, state.ultStage);
            state.ultStage++;

            if (state.ultStage >= ULT_POPS_ACTUAL) {
                state.ultWarnTicks = ULT_WARN_TICKS;
            }
            return;
        }

        state.ultWarnTicks = ULT_WARN_TICKS;
    }

    public static void cancelUltimate(ServerPlayer player, ExplosionState state, ServerLevel w) {
        state.ultActiveTicks = 0;
        state.ultWarnTicks = -1;
        state.ultStage = 0;
        state.ultWaitingLand = false;
        state.ultLaunchDelayTicks = 0;

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ExplosionDropZonePayload(player.getId(), false, 0));

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                player.getSoundSource(),
                0.9f, 1.2f);

        w.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.8, player.getZ(),
                12, 0.35, 0.25, 0.35, 0.01);

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 40, 5, true, false
        ));
    }

    public static void forceEarlyDetonation(ServerPlayer player, ExplosionState state, ServerLevel w) {
        if (state.ultAirTicks <= 0) return;

        state.ultAirTicks = 0;
        state.ultWaitingLand = false;

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ExplosionDropZonePayload(player.getId(), false, 0));

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, player.getSoundSource(), 1.0f, 1.5f);

        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.5, v.y * 0.2, v.z * 0.5);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void doUltPop(ServerPlayer player, ExplosionState state, boolean finisher, int popIndex) {
        ServerLevel w = player.serverLevel();

        Vec3 pre = player.getDeltaMovement();

        state.noSelfExpTicks = finisher ? 8 : 6;

        player.addEffect(new MobEffectInstance(ModEffects.BRACED, 200, 0, false, false, true));

        state.ultWaitingLand = true;

        if (!finisher) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ExplosionDropZonePayload(player.getId(), true, state.ultStage));
        }

        Vec3 origin = player.position().add(0, finisher ? 0.1 : 0.2, 0);

        if (finisher) {
            explodeAt(w, origin, ULT_FINAL_POWER, ULT_FINAL_BREAK_BLOCKS);
            applyExplosionDamage(player, w, origin, ULT_FINAL_DMG_RADIUS, ULT_FINAL_DAMAGE, true, false);
        } else {
            explodeAt(w, origin, ULT_POP_POWER, ULT_POP_BREAK_BLOCKS);
            applyExplosionDamage(player, w, origin, ULT_POP_DMG_RADIUS, ULT_POP_DAMAGE, true, false);
        }

        Vec3 look = player.getViewVector(1.0f);
        double launchY = finisher ? ULT_FINAL_LAUNCH_Y : ULT_POP_LAUNCH_Y;

        if (!player.onGround()) {
            launchY *= 0.5;
        }

        double targetX = pre.x + look.x * ULT_AIR_STEER;
        double targetZ = pre.z + look.z * ULT_AIR_STEER;
        double targetY = launchY;

        Vec3 vNow = player.getDeltaMovement();

        player.setDeltaMovement(vNow.x, Math.max(vNow.y, !player.onGround() ? ULT_LAUNCH_KICK_Y * 0.5 : ULT_LAUNCH_KICK_Y), vNow.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        scheduleLaunch(state, targetX, targetY, targetZ);

        float plingPitch = 1.25f + (0.12f * popIndex);
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_PLING.value(),
                player.getSoundSource(),
                0.8f, plingPitch);

        if (finisher) {
            w.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.EXPLODEBIG.get(),
                    player.getSoundSource(),
                    0.8f, 0.85f);
            CameraShake.shakeNearby(player, 14.0, 16, 1.55f);
        } else {
            float pitch = 1.05f + 0.12f * popIndex;
            w.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.EXPLODEBIG.get(),
                    player.getSoundSource(),
                    0.75f, pitch);
            CameraShake.shakeNearby(player, 9.0, 8, 0.9f);
        }
    }

    private static void tickUltAmbientFx(ServerPlayer player, ServerLevel w) {
        w.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 0.15, player.getZ(),
                1, 0.18, 0.03, 0.18, 0.002);

        if (w.getGameTime() % 3 == 0) {
            w.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    player.getX(), player.getY() + 0.25, player.getZ(),
                    1, 0.12, 0.06, 0.12, 0.01);
        }
    }

    private static void tickUltFinisherChargeFx(ServerPlayer player, ServerLevel w, int warnLeft) {
        float progress = 1.0f - (warnLeft / (float) ULT_WARN_TICKS);
        progress = Mth.clamp(progress, 0.0f, 1.0f);

        int smokeCount = 6 + (int) (progress * 16.0f);
        int flameCount = 2 + (int) (progress * 10.0f);

        w.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 0.20, player.getZ(),
                smokeCount, 0.65, 0.05, 0.65, 0.02);

        w.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                player.getX(), player.getY() + 0.35, player.getZ(),
                flameCount, 0.35, 0.12, 0.35, 0.02);

        if (w.getGameTime() % 2 == 0) {
            w.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                    player.getX(), player.getY() + 0.25, player.getZ(),
                    1, 0.25, 0.05, 0.25, 0.0);
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                ULT_CHARGE_REFRESH_TICKS,
                ULT_CHARGE_SLOWNESS_AMP,
                true,
                false
        ));
        player.setSprinting(false);

        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static boolean isShieldBlockingExplosion(ServerPlayer sp, Vec3 center) {
        if (!sp.isBlocking()) return false;

        Vec3 look = sp.getViewVector(1.0f).normalize();
        Vec3 toExplosion = center.subtract(sp.position()).normalize();

        return look.dot(toExplosion) > 0.35;
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static void explodeAt(ServerLevel w, Vec3 pos, float power, boolean breakBlocks) {
        boolean grief = breakBlocks && w.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

        w.explode(
                null,
                pos.x, pos.y, pos.z,
                0.0f,
                false,
                Level.ExplosionInteraction.NONE
        );

        w.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);

        w.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.x, pos.y, pos.z, (int)(power * 15), power * 0.4, power * 0.4, power * 0.4, 0.05);
        w.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, (int)(power * 4), power * 0.2, power * 0.2, power * 0.2, 0.1);

        if (grief) {
            java.util.Set<BlockPos> blocksToBreak = new java.util.HashSet<>();
            int rays = 16;
            float breakPower = power * 0.9f;

            for (int x = 0; x < rays; ++x) {
                for (int y = 0; y < rays; ++y) {
                    for (int z = 0; z < rays; ++z) {
                        if (x == 0 || x == rays - 1 || y == 0 || y == rays - 1 || z == 0 || z == rays - 1) {
                            double dx = (double) x / (rays - 1) * 2.0 - 1.0;
                            double dy = (double) y / (rays - 1) * 2.0 - 1.0;
                            double dz = (double) z / (rays - 1) * 2.0 - 1.0;
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            dx /= dist;
                            dy /= dist;
                            dz /= dist;

                            float currentPower = breakPower * (0.7F + w.random.nextFloat() * 0.6F);
                            double cx = pos.x;
                            double cy = pos.y;
                            double cz = pos.z;

                            for (float step = 0.3F; currentPower > 0.0F; currentPower -= 0.225F) {
                                BlockPos targetPos = BlockPos.containing(cx, cy, cz);
                                BlockState state = w.getBlockState(targetPos);

                                if (!state.isAir()) {
                                    float resistance = state.getBlock().getExplosionResistance();
                                    if (!state.getFluidState().isEmpty()) {
                                        resistance = Math.max(resistance, 100.0F);
                                    }
                                    currentPower -= (resistance + 0.3F) * 0.3F;
                                }

                                if (currentPower > 0.0F && !state.isAir() && state.getBlock().getExplosionResistance() < 1200.0F && state.getFluidState().isEmpty()) {
                                    blocksToBreak.add(targetPos);
                                }

                                cx += dx * 0.3D;
                                cy += dy * 0.3D;
                                cz += dz * 0.3D;
                            }
                        }
                    }
                }
            }

            for (BlockPos targetPos : blocksToBreak) {
                BlockState state = w.getBlockState(targetPos);

                if (state.is(BlockTags.SAND) && w.random.nextFloat() < GLASS_CONVERT_CHANCE) {
                    w.setBlock(targetPos, Blocks.GLASS.defaultBlockState(), 3);
                    w.playSound(null, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
                    continue;
                }

                boolean shouldDrop = w.random.nextFloat() < (1.0F / Math.max(1.0F, breakPower * 1.5f));
                w.destroyBlock(targetPos, shouldDrop);
            }
        }
    }

    private static void applyExplosionDamage(ServerPlayer caster, ServerLevel w,
                                             Vec3 center, double radius, float maxDamage, boolean isUltimate, boolean isSecondary) {

        AABB box = new AABB(center, center).inflate(radius, radius, radius);

        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != caster);

        DamageSource src = isUltimate ?
                ModDamageTypes.superExplosion(w, caster) :
                ModDamageTypes.explosionNormal(w, caster);

        for (LivingEntity e : hits) {
            double d = e.position().distanceTo(center);
            if (d > radius) continue;

            float t = 1.0f - (float)(d / radius);
            t = Mth.clamp(t, 0.0f, 1.0f);

            float dmg = maxDamage * (0.25f + 0.75f * (t * t));

            double knockMul = 1.0;
            if (e instanceof ServerPlayer sp && isShieldBlockingExplosion(sp, center)) {
                dmg *= 0.25f;
                knockMul = 0.25;
            }

            boolean wasAlive = e.getHealth() > 0;
            e.hurt(src, dmg);

            if (isSecondary && wasAlive && e.getHealth() <= 0 && e instanceof ServerPlayer) {
                if (w.random.nextFloat() < WHY_SOUND_CHANCE) {
                    w.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.WHY.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }

            Vec3 push = e.position().subtract(center);
            Vec3 horiz = new Vec3(push.x, 0.0, push.z);
            if (horiz.lengthSqr() > 1.0e-6) {
                Vec3 dir = horiz.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * (0.25 * t) * knockMul, 0.08 * t * knockMul, dir.z * (0.25 * t) * knockMul));
                e.hasImpulse = true;

                // Sync velocity for other players hit by explosions
                if (e instanceof ServerPlayer targetPlayer) {
                    targetPlayer.hurtMarked = true;
                    targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
                }
            }
        }
    }

    private static void scheduleLaunch(ExplosionState state, double x, double y, double z) {
        state.launchX = x;
        state.launchY = y;
        state.launchZ = z;
        state.ultLaunchDelayTicks = ULT_LAUNCH_DELAY_TICKS;
    }

    private static void tickPendingLaunch(ServerPlayer player, ExplosionState state) {
        state.ultLaunchDelayTicks--;

        if (state.ultLaunchDelayTicks == 1) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, ULT_LAUNCH_KICK_Y), v.z);
            player.hasImpulse = true;
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            return;
        }

        if (state.ultLaunchDelayTicks == 0) {
            player.setDeltaMovement(state.launchX, state.launchY, state.launchZ);
            player.hasImpulse = true;
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.explosion.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.explosion.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.explosion.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.explosion.ultimate_name").getString(); }
    @Override public String getPassiveName() { return Component.translatable("power.loopypowers.explosion.passive_name").getString(); }

    @Override public long getPrimaryCooldownMs() { return 28_000; }
    @Override public long getSecondaryCooldownMs() { return 0; }
    @Override public long getUltimateCooldownMs() { return 460_000; }

    @Override
    public String getOverviewDescription() { return Component.translatable("power.loopypowers.explosion.description.overview").getString(); }

    @Override
    public String getPassiveDescription() {return Component.translatable("power.loopypowers.explosion.description.passive").getString(); }

    @Override
    public String getPrimaryDescription() { return Component.translatable("power.loopypowers.explosion.description.primary").getString(); }

    @Override
    public String getSecondaryDescription() { return Component.translatable("power.loopypowers.explosion.description.secondary").getString(); }

    @Override
    public String getUltimateDescription() { return Component.translatable("power.loopypowers.explosion.description.ultimate").getString(); }
}