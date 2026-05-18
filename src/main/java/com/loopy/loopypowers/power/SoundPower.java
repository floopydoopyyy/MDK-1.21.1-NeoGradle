package com.loopy.loopypowers.power;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.entity.SonicBoltEntity;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.RenderPackets;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class SoundPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, SoundCasterState> CASTER_STATES = new HashMap<>();
    private static final Map<UUID, SoundVictimState> VICTIM_STATES = new HashMap<>();

    private static class SoundCasterState {
        int trailStep = 0;
        int scanStep = 0;
        int hbStep = 0;
        int ultWindup = 0;
        boolean ultPendingFire = false;
        BassDropState bdState = null;
    }

    private static class SoundVictimState {
        int score = 0;
        int resonatedTicks = 0;
        int immunityTicks = 0;
        int hbStep = 0;
        long lastSeenTick = 0;
        long lastTickTime = 0;
    }

    private static final class BassDropState {
        int nextPulse;
        int nextPulseIn;
        boolean finalPending;
        int finalIn;
        final java.util.Set<UUID> scanned = new java.util.HashSet<>();
        int pullVizStep = -1;
        int blastVizStep = -1;
    }

    private static SoundCasterState getCasterState(ServerPlayer player) {
        return CASTER_STATES.computeIfAbsent(player.getUUID(), k -> new SoundCasterState());
    }

    private static SoundVictimState getVictimState(LivingEntity victim) {
        return VICTIM_STATES.computeIfAbsent(victim.getUUID(), k -> new SoundVictimState());
    }

    /* ============================================================
       CONSTANTS & TUNING
       ============================================================ */

    // Passive
    private static final int    RES_IMMUNITY_TICKS = 140; // time after stun for immunity
    private static final double RES_RADIUS         = 14.0;
    private static final int    RES_SCAN_INTERVAL  = 4;
    private static final int    RES_TRAIL_INTERVAL = 2;
    private static final double RES_INSTANT_RADIUS = 3.0;
    private static final int    RESONATED_TICKS    = 80;
    private static final int    RES_THRESHOLD      = 15;
    private static final int    RES_DECAY_PER_SCAN = 1;
    private static final int    MAX_TRAIL_TARGETS  = 14;
    private static final float  BURST_BONUS_DAMAGE  = 7.5f;

    private static final int PTS_SLOW_MOVE = 1;
    private static final int PTS_FAST_MOVE = 2;
    private static final int PTS_SPRINT    = 3;
    private static final int PTS_JUMP      = 3;
    private static final int PTS_FALL      = 4;
    private static final int PTS_HURT      = 2;
    private static final int PTS_WATER     = 1;

    // Primary
    private static final double BOLT_SPAWN_OFFSET  = 0.6;
    private static final double BOLT_SPEED         = 1.7;

    // Secondary
    private static final int    BD_PULSE_COUNT       = 7;
    private static final int    BD_FINAL_DELAY_TICKS = 6;
    private static final double BD_PULL_RADIUS       = 10.0;
    private static final double BD_FINAL_RADIUS      = 7.0;
    private static final float  BD_PULL_STRENGTH     = 0.21f;
    private static final float  BD_PULL_UP           = 0.02f;
    private static final float  BD_FINAL_KB          = 1.15f;
    private static final float  BD_FINAL_UP          = 0.30f;
    private static final float  BD_FINAL_DAMAGE      = 16.5f;
    private static final int    BD_FINAL_STUN_TICKS  = 40;
    private static final int    BD_REMOTE_STUN_TICKS = 30;

    private static final int PULL_VIZ_STEPS  = 8;
    private static final int BLAST_VIZ_STEPS = 8;
    private static final int PULL_POINTS     = 90;
    private static final int BLAST_POINTS    = 120;

    // Ultimate
    private static final int    ULT_WINDUP_TICKS      = 28;
    private static final double ULT_RANGE             = 45.0;
    private static final double ULT_BEAM_RADIUS       = 1.35;
    private static final float  ULT_DAMAGE            = 20.5f;
    private static final float  ULT_KB                = 2.5f;
    private static final float  ULT_UP                = 1.2f;
    private static final int    ULT_TEAR_STEPS        = 36;
    private static final float  ULT_TEAR_CHANCE       = 0.45f;
    private static final float  ULT_DROP_CHANCE       = 0.15f;
    private static final double ULT_PARTICLE_STEP     = 0.55;
    private static final int    ULT_MAX_BLOCKS_BROKEN = 150;
    private static final int    ULT_SURFACE_SEARCH    = 4;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("sd_")); // Clean legacy
        CASTER_STATES.put(player.getUUID(), new SoundCasterState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("sd_"));
        CASTER_STATES.remove(player.getUUID());

        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                // Remove mid-air Sonic Bolts owned by this player
                w.getEntitiesOfClass(SonicBoltEntity.class, player.getBoundingBox().inflate(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);

                // Strip GLOWING and STUN from entities that the player resonated
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(150), LivingEntity::isAlive)) {
                    SoundVictimState state = VICTIM_STATES.get(e.getUUID());
                    if (state != null && state.resonatedTicks > 0) {
                        state.resonatedTicks = 0;
                        e.removeEffect(MobEffects.GLOWING);
                        e.removeEffect(ModEffects.STUN);
                    }
                }
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

        SoundCasterState caster = getCasterState(player);

        if (caster.trailStep > 0) caster.trailStep--;
        if (caster.scanStep > 0) caster.scanStep--;
        if (caster.hbStep > 0) caster.hbStep--;

        if (PassiveManager.isEnabled(player)) {
            tickResonancePassive(player, caster);
        }

        if (caster.bdState != null) {
            tickBassDrop(player, caster);
        }

        if (caster.ultWindup > 0) {
            tickUltimate(player, caster);
        }

        // Clean up global victims map occasionally to prevent memory leaks
        if (player.tickCount % 100 == 0) {
            long now = player.serverLevel().getGameTime();
            VICTIM_STATES.values().removeIf(v -> (now - v.lastSeenTick) > 100 && v.score <= 0 && v.resonatedTicks <= 0 && v.immunityTicks <= 0);
        }
    }

    public static void applyAbilityHit(ServerPlayer caster, LivingEntity target, float baseDamage, boolean allowBurst) {
        target.hurt(ModDamageTypes.sound(target.level(), caster), baseDamage);

        if (!allowBurst) return;

        SoundVictimState vState = VICTIM_STATES.get(target.getUUID());
        if (vState == null || vState.resonatedTicks <= 0) return;

        vState.resonatedTicks = 0;
        target.removeEffect(MobEffects.GLOWING);

        target.hurt(ModDamageTypes.sound(target.level(), caster), BURST_BONUS_DAMAGE);

        // resets velocity before stunning so they stop moving
        target.setDeltaMovement(0, Math.min(target.getDeltaMovement().y, 0.0), 0);
        target.hasImpulse = true;

        // Apply stun
        target.addEffect(new MobEffectInstance(ModEffects.STUN, 35, 0, false, false, true));
        vState.immunityTicks = RES_IMMUNITY_TICKS;
        vState.score = 0;

        if (target instanceof ServerPlayer targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 3, 15, 0.08f);
        }

        ServerLevel sw = (ServerLevel) target.level();
        sw.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0, 0, 0, 0);
        sw.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.2, target.getZ(), 6, 0.35, 0.25, 0.35, 0.02);

        sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), caster.getSoundSource(), 0.9f, 1.3f);
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void tickResonancePassive(ServerPlayer player, SoundCasterState caster) {
        ServerLevel w = player.serverLevel();
        long nowTick = w.getGameTime();

        AABB box = new AABB(player.position(), player.position()).inflate(RES_RADIUS, 6.0, RES_RADIUS);
        List<LivingEntity> nearby = w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        if (caster.trailStep <= 0) {
            caster.trailStep = RES_TRAIL_INTERVAL;
            int sent = 0;
            for (LivingEntity e : nearby) {
                if (sent >= MAX_TRAIL_TARGETS) break;
                RenderPackets.sendResonanceTrail(player, e.getId(), 2, 1.0f);
                sent++;
            }
        }

        if (caster.scanStep > 0) return;
        caster.scanStep = RES_SCAN_INTERVAL;

        for (LivingEntity e : nearby) {
            SoundVictimState vState = getVictimState(e);
            vState.lastSeenTick = nowTick;

            // Decouple ticking from the 4-tick scan loop to ensure accurate countdowns
            if (vState.lastTickTime != nowTick) {
                int delta = (int) (nowTick - vState.lastTickTime);
                if (delta > 20) delta = 20; // prevent massive jumps
                vState.lastTickTime = nowTick;

                boolean wasResonated = vState.resonatedTicks > 0;
                if (vState.resonatedTicks > 0) vState.resonatedTicks -= delta;

                // If resonance naturally expired this tick, start immunity
                if (wasResonated && vState.resonatedTicks <= 0) {
                    vState.immunityTicks = RES_IMMUNITY_TICKS;
                    vState.score = 0;
                }

                if (vState.immunityTicks > 0) vState.immunityTicks -= delta;
                if (vState.hbStep > 0) vState.hbStep -= delta;
            }

            // Block score buildup and instant radius if immune
            if (vState.immunityTicks > 0) {
                vState.score = 0;
            } else {
                vState.score = Math.max(0, vState.score - RES_DECAY_PER_SCAN);
                vState.score += computeConspicuousPoints(e);

                if (player.distanceToSqr(e) <= (RES_INSTANT_RADIUS * RES_INSTANT_RADIUS)) {
                    vState.score = RES_THRESHOLD;
                }
            }

            if (vState.score < RES_THRESHOLD) {
                float frac = vState.score / (float) RES_THRESHOLD;
                if (vState.resonatedTicks <= 0 && frac >= 0.70f) {
                    if (caster.hbStep <= 0 && vState.hbStep <= 0) {
                        float t = Mth.clamp((frac - 0.70f) / 0.30f, 0.0f, 1.0f);
                        int interval = (int) Mth.lerp(t, 16.0f, 5.0f);

                        caster.hbStep = Math.max(3, interval - 2);
                        vState.hbStep = interval;

                        float vol = 0.25f + 0.35f * t;
                        float pitch = 0.85f + 0.25f * t;

                        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, vol, pitch);

                        if (e instanceof ServerPlayer spTarget) {
                            w.playSound(null, spTarget.getX(), spTarget.getY(), spTarget.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, vol * 0.9f, pitch);
                        }

                        if (frac >= 0.92f) {
                            RenderPackets.sendResonanceRing(player, e.getId(), 1.2f);
                            if (e instanceof ServerPlayer spTarget2) {
                                RenderPackets.sendResonanceRing(spTarget2, e.getId(), 1.0f);
                            }
                        }
                    }
                }
            }

            if (vState.score >= RES_THRESHOLD) {
                boolean already = vState.resonatedTicks > 0;

                vState.resonatedTicks = RESONATED_TICKS;
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, RESONATED_TICKS + 60, 0, true, true));

                if (!already) {
                    w.playSound(null, e.getX(), e.getY(), e.getZ(),
                            SoundEvents.SCULK_CLICKING,
                            player.getSoundSource(),
                            0.7f, 1.1f);

                    w.sendParticles(ParticleTypes.SCULK_SOUL,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            6, 0.35, 0.45, 0.35, 0.02);

                    RenderPackets.sendResonanceLine(player, e.getId(), 20, 1.0f);
                    if (e instanceof ServerPlayer spTarget) {
                        RenderPackets.sendResonanceLine(spTarget, e.getId(), 20, 1.0f);
                    }
                }
            }
        }
    }

    private static int computeConspicuousPoints(LivingEntity e) {
        int pts = 0;
        Vec3 v = e.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);

        if (h > 0.08) pts += PTS_SLOW_MOVE;
        if (h > 0.22) pts += PTS_FAST_MOVE;
        if (e.isSprinting()) pts += PTS_SPRINT;
        if (!e.onGround() && v.y > 0.10) pts += PTS_JUMP;
        if (!e.onGround() && v.y < -0.25) pts += PTS_FALL;
        if (e.hurtTime > 0) pts += PTS_HURT;
        if (e.isInWater()) pts += PTS_WATER;
        return pts;
    }

    /* ============================================================
       PRIMARY  –  Doppler
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        SonicBoltEntity bolt = new SonicBoltEntity(ModEntities.SONIC_BOLT.get(), w);
        bolt.setOwner(player);

        Vec3 start = player.getEyePosition().add(player.getViewVector(1.0f).scale(BOLT_SPAWN_OFFSET));
        bolt.setPos(start.x, start.y, start.z);

        Vec3 dir = player.getViewVector(1.0f).normalize();
        bolt.setDeltaMovement(dir.scale(BOLT_SPEED));

        w.addFreshEntity(bolt);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BOLT.get(), player.getSoundSource(), 0.4f, 1.2f);
    }

    /* ============================================================
       SECONDARY  –  Bass Drop
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        SoundCasterState caster = getCasterState(player);

        BassDropState s = new BassDropState();
        s.nextPulse = BD_PULSE_COUNT;
        s.nextPulseIn = 0; // do first one instatly now
        s.finalPending = false;

        caster.bdState = s;

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, player.getSoundSource(), 0.7f, 1.4f);
        w.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
    }

    private static void tickBassDrop(ServerPlayer caster, SoundCasterState state) {
        BassDropState s = state.bdState;
        ServerLevel w = caster.serverLevel();

        animateBassSpheres(w, caster, s);

        if (!s.finalPending) {
            s.nextPulseIn--;

            if (s.nextPulseIn <= 0 && s.nextPulse > 0) {
                doBassPullPulse(w, caster, s);
                s.nextPulse--;

                // spacing accel
                s.nextPulseIn = Math.max(2, s.nextPulse * 2);

                if (s.nextPulse <= 0) {
                    s.finalPending = true;
                    s.finalIn = BD_FINAL_DELAY_TICKS;
                }
            }
            return;
        }

        s.finalIn--;
        if (s.finalIn > 0) return;

        doBassFinalBurst(w, caster, state, s.scanned);
        state.bdState = null;
    }

    private static void doBassPullPulse(ServerLevel w, ServerPlayer caster, BassDropState s) {
        Vec3 cPos = caster.position();

        AABB box = new AABB(cPos, cPos).inflate(BD_PULL_RADIUS, 6.0, BD_PULL_RADIUS);
        List<LivingEntity> targets = w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        // pitch accel
        float pitch = 1.0f + ((BD_PULSE_COUNT - s.nextPulse) * 0.15f);

        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.BASSSINGLE.get(), caster.getSoundSource(), 0.6f, pitch);
        w.sendParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);

        spawnPullContractingSphere(w, cPos);
        spawnSphereShell(w, cPos, BD_PULL_RADIUS * 0.65, 12, ParticleTypes.SCULK_SOUL, 0.9);

        s.pullVizStep = 0;

        for (LivingEntity t : targets) {
            Vec3 toCaster = cPos.subtract(t.position());
            Vec3 horiz = new Vec3(toCaster.x, 0.0, toCaster.z);
            if (horiz.lengthSqr() < 1.0e-6) continue;

            Vec3 dir = horiz.normalize();
            t.setDeltaMovement(t.getDeltaMovement().add(dir.x * BD_PULL_STRENGTH, BD_PULL_UP, dir.z * BD_PULL_STRENGTH));
            t.hasImpulse = true;

            w.sendParticles(ParticleTypes.SCULK_CHARGE_POP, t.getX(), t.getY() + t.getBbHeight() * 0.55, t.getZ(), 2, 0.12, 0.10, 0.12, 0.01);
            s.scanned.add(t.getUUID());
        }
    }

    private static void doBassFinalBurst(ServerLevel w, ServerPlayer caster, SoundCasterState state, java.util.Set<UUID> scanned) {
        Vec3 cPos = caster.position();

        AABB box = new AABB(cPos, cPos).inflate(BD_FINAL_RADIUS, 6.0, BD_FINAL_RADIUS);
        List<LivingEntity> nearby = w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.BASSDROP.get(), caster.getSoundSource(), 1.0f, 1.00f);
        w.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cPos.x, cPos.y + 0.25, cPos.z, 1, 0, 0, 0, 0);
        w.sendParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);

        spawnFinalExpandingSphere(w, cPos);
        spawnSphereShell(w, cPos, BD_FINAL_RADIUS * 0.85, 18, ParticleTypes.SCULK_SOUL, 0.9);

        if (state.bdState != null) state.bdState.blastVizStep = 0;

        for (LivingEntity t : nearby) {
            applyBassBurstHit(caster, t, BD_FINAL_DAMAGE, BD_FINAL_STUN_TICKS);
        }

        var server = caster.getServer();
        if (server == null) return;

        for (UUID id : scanned) {
            LivingEntity t = null;
            for (ServerLevel ww : server.getAllLevels()) {
                Entity e = ww.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive()) { t = le; break; }
            }
            if (t == null || t == caster) continue;

            if (t.level() == w && t.distanceToSqr(caster) <= (BD_FINAL_RADIUS * BD_FINAL_RADIUS)) continue;

            if (t.level() instanceof ServerLevel tw) {
                tw.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.2, t.getZ(), 6, 0.35, 0.20, 0.35, 0.02);
                tw.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_EXPLODE.value(), caster.getSoundSource(), 0.7f, 1.3f);
            }
            applyBassBurstHit(caster, t, 0.0f, BD_REMOTE_STUN_TICKS);
        }
        CameraShake.shakeNearby(caster, 5,15, 0.04f);
    }

    private static void applyBassBurstHit(ServerPlayer caster, LivingEntity target, float damage, int stunTicks) {
        if (damage > 0.0f) {
            target.hurt(ModDamageTypes.sound(target.level(), caster), damage);
        }

        Vec3 dir = target.position().subtract(caster.position());
        dir = new Vec3(dir.x, 0.0, dir.z);
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(dir.x * BD_FINAL_KB, BD_FINAL_UP, dir.z * BD_FINAL_KB));
        target.hasImpulse = true;

        // Apply STUN Effect if resonated
        SoundVictimState vState = VICTIM_STATES.get(target.getUUID());
        if (vState != null && vState.resonatedTicks > 0) {
            vState.resonatedTicks = 0;
            target.removeEffect(MobEffects.GLOWING);
            target.addEffect(new MobEffectInstance(ModEffects.STUN, stunTicks, 0, false, false, true));

            vState.immunityTicks = RES_IMMUNITY_TICKS;
            vState.score = 0;

            if (target instanceof ServerPlayer spTarget) {
                // holllyyy fuckinggg shittt i hate this new sound system
                spTarget.serverLevel().playSound(null, spTarget.getX(), spTarget.getY(), spTarget.getZ(), ModSounds.EARRING.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
            }
        }

        if (target instanceof ServerPlayer spTarget) {
            CameraShake.shakeNearby(spTarget, 14, 15, 1.4f);
        }
    }

    private static void spawnSphereShell(ServerLevel w, Vec3 center, double radius, int points, net.minecraft.core.particles.ParticleOptions particle, double yOffset) {
        for (int i = 0; i < points; i++) {
            double u = w.random.nextDouble();
            double v = w.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            double j = 0.12;
            double px = center.x + sx * radius + (w.random.nextDouble() - 0.5) * j;
            double py = center.y + yOffset + sy * radius + (w.random.nextDouble() - 0.5) * j;
            double pz = center.z + sz * radius + (w.random.nextDouble() - 0.5) * j;

            w.sendParticles(particle, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnPullContractingSphere(ServerLevel w, Vec3 center) {
        int shells = 10;
        for (int s = 0; s < shells; s++) {
            double t = s / (double)(shells - 1);
            double r = Mth.lerp(t, BD_PULL_RADIUS, 5.2);
            spawnSphereShell(w, center, r, 120, ParticleTypes.SCULK_CHARGE_POP, 0.9);
        }
    }

    private static void spawnFinalExpandingSphere(ServerLevel w, Vec3 center) {
        int shells = 10;
        for (int s = 0; s < shells; s++) {
            double t = s / (double)(shells - 1);
            double r = Mth.lerp(t, 1.0, BD_FINAL_RADIUS);
            spawnSphereShell(w, center, r, 150, ParticleTypes.SCULK_CHARGE_POP, 0.9);
        }
    }

    private static void animateBassSpheres(ServerLevel w, ServerPlayer caster, BassDropState s) {
        if (s.pullVizStep >= 0 && s.pullVizStep < PULL_VIZ_STEPS) {
            Vec3 c = caster.position().add(0, 0.9, 0);
            double t = (PULL_VIZ_STEPS <= 1) ? 1.0 : (s.pullVizStep / (double)(PULL_VIZ_STEPS - 1));
            t = t * t;
            double r = Mth.lerp(t, BD_PULL_RADIUS, 2.6);

            spawnSphereShell(w, c, r, PULL_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0);

            if ((s.pullVizStep & 1) == 0) {
                spawnSphereShell(w, c, r * 0.55, 22, ParticleTypes.SCULK_SOUL, 0.0);
            }

            s.pullVizStep++;
            if (s.pullVizStep >= PULL_VIZ_STEPS) s.pullVizStep = -1;
        }

        if (s.blastVizStep >= 0 && s.blastVizStep < BLAST_VIZ_STEPS) {
            Vec3 c = caster.position().add(0, 0.9, 0);
            double t = (BLAST_VIZ_STEPS <= 1) ? 1.0 : (s.blastVizStep / (double)(BLAST_VIZ_STEPS - 1));
            double ease = 1.0 - Math.pow(1.0 - t, 2.0);
            double r = Mth.lerp(ease, 1.0, BD_FINAL_RADIUS * 1.5);

            spawnSphereShell(w, c, r, BLAST_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0);

            if ((s.blastVizStep & 1) == 0) {
                spawnBassGroundRing(w, caster.position(), r);
            }

            s.blastVizStep++;
            if (s.blastVizStep >= BLAST_VIZ_STEPS) s.blastVizStep = -1;
        }
    }

    private static void spawnBassGroundRing(ServerLevel w, Vec3 center, double radius) {
        double y = center.y + 0.10;
        for (int i = 0; i < 36; i++) {
            double a = (Math.PI * 2.0) * (i / 36.0);
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;

            double j = 0.06;
            x += (w.random.nextDouble() - 0.5) * j;
            z += (w.random.nextDouble() - 0.5) * j;

            w.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /* ============================================================
       ULTIMATE  –  Sonic Shriek
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        SoundCasterState caster = getCasterState(player);

        caster.ultWindup = ULT_WINDUP_TICKS;
        caster.ultPendingFire = true;

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, player.getSoundSource(), 1.0f, 1.0f);
        w.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        CameraShake.shakeNearby(player, 10.0, 8, 0.9f);
    }

    private static void tickUltimate(ServerPlayer player, SoundCasterState state) {
        if (state.ultWindup <= 0) return;
        state.ultWindup--;

        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
        player.hasImpulse = true;
        player.setSprinting(false);
        player.fallDistance = 0.0f;

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 10, true, false));

        if (player.level() instanceof ServerLevel w) {
            if (state.ultWindup % 4 == 0) {
                w.sendParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.35, 0.25, 0.01);
            }
        }

        if (state.ultWindup > 0) return;

        if (!state.ultPendingFire) return;
        state.ultPendingFire = false;

        fireUltimateBeam(player);
    }

    private static void fireUltimateBeam(ServerPlayer caster) {
        ServerLevel w = caster.serverLevel();

        Vec3 start = caster.getEyePosition();
        Vec3 dir = caster.getViewVector(1.0f).normalize();
        Vec3 end = start.add(dir.scale(ULT_RANGE));

        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.RAILGUN.get(), caster.getSoundSource(), 1.2f, 0.9f);
        CameraShake.shakeNearby(caster, 20.0, 18, 1.6f);

        spawnBeamParticles(w, start, end);

        AABB search = new AABB(start, end).inflate(ULT_BEAM_RADIUS + 1.0);
        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, search, e -> e.isAlive() && e != caster);

        for (LivingEntity target : hits) {
            if (!segmentIntersectsExpandedAabb(start, end, target.getBoundingBox().inflate(ULT_BEAM_RADIUS))) continue;

            SoundVictimState vState = VICTIM_STATES.get(target.getUUID());
            if (vState != null && vState.resonatedTicks > 0) {
                applyAbilityHit(caster, target, 0.0f, true);
            }

            target.hurt(ModDamageTypes.sound(target.level(), caster), ULT_DAMAGE);
            target.setDeltaMovement(target.getDeltaMovement().add(dir.x * ULT_KB, ULT_UP, dir.z * ULT_KB));
            target.hasImpulse = true;
        }

        tearGroundAlongBeam(w, start, end);
    }

    private static void spawnBeamParticles(ServerLevel w, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        int steps = Mth.clamp((int)(len / ULT_PARTICLE_STEP), 10, 220);
        Vec3 step = delta.scale(1.0 / steps);

        Vec3 p = start;
        for (int i = 0; i <= steps; i++) {
            w.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);

            if ((i & 1) == 0) {
                w.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.x, p.y, p.z, 3, 0.18, 0.18, 0.18, 0.01);
            } else {
                w.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 1, 0.10, 0.10, 0.10, 0.0);
            }
            p = p.add(step);
        }

        w.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 1, 0, 0, 0, 0);
    }

    private static void tearGroundAlongBeam(ServerLevel w, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        int steps = Mth.clamp(ULT_TEAR_STEPS, 8, 80);
        Vec3 step = delta.scale(1.0 / steps);

        int broken = 0;
        BlockPos lastBoom = null;
        int boomCooldown = 0;

        Vec3 p = start;
        for (int i = 0; i <= steps; i++) {
            if (broken >= ULT_MAX_BLOCKS_BROKEN) break;

            if (boomCooldown > 0) boomCooldown--;

            BlockPos beamPos = BlockPos.containing(p.x, p.y, p.z);
            BlockState beamState = w.getBlockState(beamPos);

            float bh = beamState.getDestroySpeed(w, beamPos);
            boolean collides = !beamState.isAir() && bh >= 0.0f && bh < 30.0f && w.getBlockEntity(beamPos) == null;

            if (collides && boomCooldown <= 0) {
                if (lastBoom == null || lastBoom.distManhattan(beamPos) >= 2) {
                    w.sendParticles(ParticleTypes.EXPLOSION_EMITTER, beamPos.getX() + 0.5, beamPos.getY() + 0.5, beamPos.getZ() + 0.5, 1, 0, 0, 0, 0);
                    w.playSound(null, beamPos.getX() + 0.5, beamPos.getY() + 0.5, beamPos.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.2f);
                    broken += breakBlastCapped(w, beamPos, (ULT_MAX_BLOCKS_BROKEN - broken));
                    lastBoom = beamPos;
                    boomCooldown = 3;
                }
            }

            if (broken >= ULT_MAX_BLOCKS_BROKEN) break;

            BlockPos base = BlockPos.containing(p.x, p.y, p.z);
            BlockPos hitPos = null;
            BlockState hitState = null;

            for (int dy = 0; dy <= ULT_SURFACE_SEARCH; dy++) {
                BlockPos q = base.below(dy);
                BlockState s = w.getBlockState(q);

                if (s.isAir()) continue;
                if (w.getBlockEntity(q) != null) continue;
                if (s.getDestroySpeed(w, q) < 0.0f) continue;
                if (s.getDestroySpeed(w, q) >= 30.0f) continue;

                hitPos = q;
                hitState = s;
                break;
            }

            if (hitPos != null) {
                if (w.random.nextFloat() < ULT_TEAR_CHANCE) {
                    w.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, hitState), hitPos.getX() + 0.5, hitPos.getY() + 0.75, hitPos.getZ() + 0.5, 12, 0.35, 0.25, 0.35, 0.10);
                    w.sendParticles(ParticleTypes.CRIT, hitPos.getX() + 0.5, hitPos.getY() + 1.1, hitPos.getZ() + 0.5, 2, 0.10, 0.10, 0.10, 0.0);
                }

                boolean drop = w.random.nextFloat() < ULT_DROP_CHANCE;
                if (w.destroyBlock(hitPos, drop)) {
                    broken++;
                }
            }
            p = p.add(step);
        }
    }

    // OTHER HELPER
    public static boolean cleanseResonance(LivingEntity target) {
        SoundVictimState state = VICTIM_STATES.get(target.getUUID());
        if (state != null && (state.score > 0 || state.resonatedTicks > 0)) {
            state.score = 0;
            state.resonatedTicks = 0;
            state.immunityTicks = RES_IMMUNITY_TICKS;

            // Remove the glowing outline if they were fully resonated
            target.removeEffect(MobEffects.GLOWING);
            return true;
        }
        return false;
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return Component.translatable("power.loopypowers.sound.name").getString(); }
    @Override public String getPassiveName()   { return Component.translatable("power.loopypowers.sound.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Component.translatable("power.loopypowers.sound.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.sound.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Component.translatable("power.loopypowers.sound.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 26_000; }
    @Override public long getUltimateCooldownMs()  { return 290_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.sound.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.sound.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.sound.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.sound.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.sound.description.ultimate").getString();
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static int breakBlastCapped(ServerLevel w, BlockPos center, int budgetLeft) {
        if (budgetLeft <= 0) return 0;
        int broken = 0;
        int radius = 2;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (broken >= budgetLeft) return broken;

                    int md = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (md > radius + 1) continue;

                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState s = w.getBlockState(p);

                    if (s.isAir()) continue;
                    if (s.getDestroySpeed(w, p) < 0.0f) continue;
                    if (w.getBlockEntity(p) != null) continue;
                    if (s.getDestroySpeed(w, p) >= 30.0f) continue;

                    if (w.random.nextFloat() < 0.35f) {
                        w.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.08);
                    }
                    boolean drop = w.random.nextFloat() < ULT_DROP_CHANCE;
                    if (w.destroyBlock(p, drop)) {
                        broken++;
                    }
                }
            }
        }
        return broken;
    }

    private static boolean segmentIntersectsExpandedAabb(Vec3 a, Vec3 b, AABB box) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.001) return box.contains(a);

        int steps = Mth.clamp((int)(len * 6.0), 12, 220);
        Vec3 step = d.scale(1.0 / steps);

        Vec3 p = a;
        for (int i = 0; i <= steps; i++) {
            if (box.contains(p)) return true;
            p = p.add(step);
        }
        return false;
    }
}