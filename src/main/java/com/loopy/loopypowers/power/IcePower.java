package com.loopy.loopypowers.power;

import com.loopy.loopypowers.block.ModBlocks;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import com.loopy.loopypowers.damage.ModDamageTypes;

import java.util.*;

public class IcePower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, IceCasterState> CASTER_STATES = new HashMap<>();
    private static final Map<UUID, IceVictimState> VICTIM_STATES = new HashMap<>();

    private static class SnowmanBuild {
        int step = 1;
        int delay = 10;
        BlockPos pos;
        SnowmanBuild(BlockPos pos) { this.pos = pos; }
    }

    private static class IceCasterState {
        int beamChargeTicks = 0;
        int beamFireTicks = 0;
        int ultActiveTicks = 0;
        int ultPulseTicks = 0;
        List<SnowmanBuild> snowmen = new ArrayList<>();
    }

    private static class IceVictimState {
        int freezePoints = 0;
        int freezeDecayTicks = 0;
        int freezeImmuneTicks = 0;
        int lastSpikeHitTick = -1;
        int lastWaveHitId = -1;
        ResourceKey<Level> worldKey;

        IceVictimState(ResourceKey<Level> worldKey) {
            this.worldKey = worldKey;
        }
    }

    private static IceCasterState getCasterState(ServerPlayer player) {
        return CASTER_STATES.computeIfAbsent(player.getUUID(), k -> new IceCasterState());
    }

    private static IceVictimState getVictimState(LivingEntity victim) {
        ServerLevel w = (ServerLevel) victim.level();
        return VICTIM_STATES.computeIfAbsent(victim.getUUID(), k -> new IceVictimState(w.dimension()));
    }

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("ice_")); // Clean legacy tags
        CASTER_STATES.put(player.getUUID(), new IceCasterState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("ice_"));
        CASTER_STATES.remove(player.getUUID());

        // Remove lingering slow statuses
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                // Thaw all entities
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(150), LivingEntity::isAlive)) {
                    IceVictimState state = VICTIM_STATES.get(e.getUUID());
                    if (state != null && state.freezePoints > 0) {
                        state.freezePoints = 0;
                        e.setTicksFrozen(0);
                        e.removeEffect(ModEffects.DEEPFREEZE);
                    }
                }

                // Clear any pending global instances owned by this player
                SPIKE_CASTS.removeIf(sc -> sc.owner.equals(player.getUUID()));
                ULT_WAVES.removeIf(uw -> uw.owner.equals(player.getUUID()));
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

        tickFrozenWorld(player.serverLevel());
        tickSpikesWorld(player.serverLevel());
        tickBeam(player);
        tickUltimate(player);
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        // dodge if passive off
        if (!PassiveManager.isEnabled(attacker)) return;
        shatterOrFreeze(attacker, target, FRZ_POINTS_MELEE);
    }

   /* ============================================================
   PASSIVE
   ============================================================ */

    // Point tuning
    private static final int FRZ_MAX_POINTS = 100;

    // Stage thresholds (0 = no freeze)
    private static final int FRZ_STAGE_1 = 10;    // light particles
    private static final int FRZ_STAGE_2 = 40;   // Slowness 1
    private static final int FRZ_STAGE_3 = 60;   // Slowness 2
    private static final int FRZ_STAGE_4 = 80;   // Slowness 2 + Mining Fatigue 1
    private static final int FRZ_STAGE_5 = 95;   // Fully frozen

    // “Quickly thaw out if not being damaged”
    private static final int FRZ_DECAY_DELAY_TICKS = 20;  // how long after last hit before freeze decays
    private static final int FRZ_DECAY_STEP_TICKS  = 10;   // after delay, decay every this many ticks
    private static final int FRZ_DECAY_POINTS_STEP = 5;   // how many points decay
    // Shatter
    private static final int   FRZ_IMMUNE_TICKS = 100;     // time of ice immunity after shatter
    private static final float SHATTER_BONUS_DAMAGE = 12.0f; // damage on shatter

    // How many points abilities add
    private static final int FRZ_POINTS_MELEE = 7;  // melee hits

    // particles
    private static final DustParticleOptions FRZ_BLUE_DUST =
            new DustParticleOptions(new Vector3f(0.25f, 0.65f, 1.00f), 0.75f);
    private static final DustParticleOptions FRZ_SHIMMER_DUST =
            new DustParticleOptions(new Vector3f(0.75f, 0.95f, 1.00f), 0.45f);

    private static final Map<ResourceKey<Level>, Long> FROZEN_LAST_TICK = new HashMap<>();

    private static int getFreezeStage(int points) {
        if (points < FRZ_STAGE_1) return 0;
        if (points < FRZ_STAGE_2) return 1;
        if (points < FRZ_STAGE_3) return 2;
        if (points < FRZ_STAGE_4) return 3;
        if (points < FRZ_STAGE_5) return 4;
        return 5;
    }

    /** Calculates the EXACT time until the target thaws and updates the UI timer. */
    private static void syncFreezeTimer(LivingEntity e, IceVictimState state) {
        if (state.freezePoints <= 0) {
            e.removeEffect(ModEffects.DEEPFREEZE);
            return;
        }

        // Calculate exact ticks until thaw based on the decay intervals
        int steps = (int) Math.ceil((double) state.freezePoints / FRZ_DECAY_POINTS_STEP);
        int ticksRemaining = state.freezeDecayTicks + Math.max(0, (steps - 1) * FRZ_DECAY_STEP_TICKS);

        // Remove and reapply to force the UI to update the countdown safely
        e.removeEffect(ModEffects.DEEPFREEZE);
        e.addEffect(new MobEffectInstance(ModEffects.DEEPFREEZE, ticksRemaining, 0, false, false, true));
    }

    private static void applyFreezePoints(ServerPlayer caster, LivingEntity target, int addPoints) {
        if (addPoints <= 0) return;

        IceVictimState state = getVictimState(target);
        if (state.freezeImmuneTicks > 0) return;

        state.freezePoints = Mth.clamp(state.freezePoints + addPoints, 0, FRZ_MAX_POINTS);
        state.freezeDecayTicks = FRZ_DECAY_DELAY_TICKS;

        // Force a UI update immediately when hit
        syncFreezeTimer(target, state);

        ServerLevel w = caster.serverLevel();
        spawnFreezeStageParticles(w, target, getFreezeStage(state.freezePoints));
    }

    private static void shatterOrFreeze(ServerPlayer caster, LivingEntity target, int freezeStacks) {
        IceVictimState state = getVictimState(target);
        if (state.freezeImmuneTicks > 0) return;

        if (state.freezePoints >= FRZ_STAGE_5) {
            ServerLevel w = caster.serverLevel();

            // Bonus damage
            target.hurt(ModDamageTypes.iceShatter(w, caster), SHATTER_BONUS_DAMAGE);

            // FX
            doShatterFX(w, target);

            // sound
            w.playSound(
                    null, // null
                    target.blockPosition(),
                    ModSounds.SHATTER.get(),
                    SoundSource.PLAYERS,
                    0.5f,   // volume
                    0.8f    // pitch
            );

            // camerashake
            CameraShake.shakeNearby(caster, 6, 8, 0.35f);

            // Clear freeze + grant immunity
            clearFreeze(target);
            state.freezeImmuneTicks = FRZ_IMMUNE_TICKS;
        } else {
            applyFreezePoints(caster, target, freezeStacks);
        }
    }

    private static void doShatterFX(ServerLevel w, LivingEntity target) {
        Vec3 p = target.position().add(0, target.getBbHeight() * 0.55, 0);

        w.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 60, 0.35, 0.35, 0.35, 0.00);
        w.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 18, 0.25, 0.20, 0.25, 0.00);
        w.sendParticles(FRZ_BLUE_DUST, p.x, p.y, p.z, 24, 0.25, 0.20, 0.25, 0.00);
        w.sendParticles(FRZ_SHIMMER_DUST, p.x, p.y, p.z, 16, 0.22, 0.18, 0.22, 0.00);

        w.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9f, 1.15f);
        w.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9f, 1.55f);
    }

    private static void clearFreeze(LivingEntity e) {
        IceVictimState state = VICTIM_STATES.get(e.getUUID());
        if (state != null) {
            state.freezePoints = 0;
            state.freezeDecayTicks = 0;
        }

        e.setTicksFrozen(0);
        e.removeEffect(ModEffects.DEEPFREEZE); // clear visual indicator
    }

    private static void tickFrozenWorld(ServerLevel w) {
        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();
        Long last = FROZEN_LAST_TICK.get(key);
        if (last != null && last == now) return;
        FROZEN_LAST_TICK.put(key, now);

        if (VICTIM_STATES.isEmpty()) return;

        Iterator<Map.Entry<UUID, IceVictimState>> it = VICTIM_STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, IceVictimState> entry = it.next();
            IceVictimState state = entry.getValue();

            if (!state.worldKey.equals(key)) continue;

            Entity ent = w.getEntity(entry.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }

            // Tick immunity
            if (state.freezeImmuneTicks > 0) state.freezeImmuneTicks--;

            int points = state.freezePoints;
            if (points <= 0) {
                // stop tracking if no freeze + no immunity
                if (state.freezeImmuneTicks <= 0) it.remove();
                continue;
            }

            // Sync the effect periodically in case they re-logged or the client desyncs
            if (now % 10 == 0 || !le.hasEffect(ModEffects.DEEPFREEZE)) {
                syncFreezeTimer(le, state);
            }

            // Stage effects & particles
            int stage = getFreezeStage(points);
            applyFreezeStageEffects(w, le, stage);

            // Decay logic:
            if (state.freezeDecayTicks > 0) {
                state.freezeDecayTicks--;
                if (state.freezeDecayTicks == 0) {
                    state.freezePoints = Math.max(0, points - FRZ_DECAY_POINTS_STEP);
                    if (state.freezePoints > 0) {
                        state.freezeDecayTicks = FRZ_DECAY_STEP_TICKS;
                    } else {
                        clearFreeze(le);
                        if (state.freezeImmuneTicks <= 0) it.remove();
                    }
                }
            }
        }
    }

    private static void applyFreezeStageEffects(ServerLevel w, LivingEntity e, int stage) {
        // keep durations short so “clearing stages” feels instant (effects fall off quickly)
        final int T = 10;

        // particles scale with stage
        spawnFreezeStageParticles(w, e, stage);

        if (stage >= 2) {
            int slowAmp = (stage >= 3) ? 1 : 0; // stage2->0, stage3+->1
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, T, slowAmp, true, false));
        }
        if (stage >= 4) {
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, T, 0, true, false)); // MF1
        }
        if (stage >= 5) {
            // “can barely move” + mining fatigue stronger
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, T, 4, true, false));        // very slow
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, T, 2, true, false));  // heavy mining fatigue

            // extra movement clamp (feels frozen even if they have speed boosts etc.)
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.25, Mth.clamp(v.y, -0.5, 0.5), v.z * 0.25);
            e.hasImpulse = true;
            e.fallDistance = 0.0f;

            // strong frozen overlay (visual)
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
        }
    }

    private static void spawnFreezeStageParticles(ServerLevel w, LivingEntity e, int stage) {
        if (stage <= 0) return;

        Vec3 p = e.position().add(0, e.getBbHeight() * 0.55, 0);

        // keep it cheap: stage 1/2 spawn less often
        if (stage <= 2 && (w.getGameTime() & 1) == 1) return;

        int snow = switch (stage) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 7;
            default -> 12; // stage 5
        };

        w.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, snow, 0.18, 0.22, 0.18, 0.00);

        if (stage >= 2) {
            w.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 1, 0.15, 0.18, 0.15, 0.00);
        }
        if (stage >= 3 && (w.getGameTime() % 3) == 0) {
            w.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.12, 0.12, 0.12, 0.00);
        }
        if (stage >= 4) {
            w.sendParticles(FRZ_BLUE_DUST, p.x, p.y, p.z, 1, 0.10, 0.10, 0.10, 0.00);
        }
        if (stage >= 5) {
            // IMPORTANT: very clear “ready to shatter” indicator
            spawnShatterReadyParticles(w, e);
        }
    }

    private static void spawnShatterReadyParticles(ServerLevel w, LivingEntity e) {
        // pulse every other tick
        if ((w.getGameTime() & 1) == 1) return;

        Vec3 c = e.position().add(0, e.getBbHeight() * 0.72, 0);

        // rotating halo ring around the upper body
        double baseAng = w.getGameTime() * 0.35;
        double pulse = 0.06 * Math.sin(w.getGameTime() * 0.45);
        double r = 0.55 + pulse;

        int points = 14;
        for (int i = 0; i < points; i++) {
            double a = baseAng + (i * (Math.PI * 2.0 / points));
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + (Math.sin(a * 2.0) * 0.05);

            // bright “shatter ready” sparkles
            w.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.0);

            // shimmer + blue crackle mixed in
            if ((i % 2) == 0) {
                w.sendParticles(FRZ_SHIMMER_DUST, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if ((i % 3) == 0) {
                w.sendParticles(FRZ_BLUE_DUST, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if ((i % 4) == 0) {
                w.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        if ((w.getGameTime() % 6) == 0) {
            w.sendParticles(ParticleTypes.ENCHANT, c.x, c.y - 0.15, c.z, 6, 0.25, 0.22, 0.25, 0.0);
            w.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y - 0.15, c.z, 10, 0.28, 0.22, 0.28, 0.0);
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    private static final long PRIMARY_COOLDOWN_MS = 13_000;

    private static final double SPIKES_RANGE = 12.0;

    private static final int SPIKES_COUNT = 9;
    private static final int SPIKES_SPREAD_RADIUS = 1;

    private static final int SPIKES_TERRAIN_SEARCH = 3;

    private static final int SPIKES_MAX_HEIGHT = 3;
    private static final int SPIKES_RISE_INTERVAL = 2;

    private static final double SPIKES_MAX_Y_VEL = 1.65;
    private static final float SPIKES_DAMAGE = 8.5f;
    private static final double SPIKES_KNOCKUP_Y = 0.75;
    private static final int SPIKES_FREEZE_STACKS = 15;

    private static final double SPIKES_TRAVEL_SPEED = 0.9;
    private static final int SPIKES_TRAVEL_MIN_TICKS = 6;
    private static final int SPIKES_TRAVEL_MAX_TICKS = 16;

    private static final DustParticleOptions SPIKE_TRAIL_DUST =
            new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1.00f), 1.10f);

    private static final DustParticleOptions SPIKE_PUFF_DUST =
            new DustParticleOptions(new Vector3f(0.35f, 0.80f, 1.00f), 1.35f);

    private static final List<SpikeCast> SPIKE_CASTS = new ArrayList<>();
    private static final Map<ResourceKey<Level>, Long> SPIKES_LAST_TICK = new HashMap<>();

    private static final class SpikeBase {
        final BlockPos base;
        final int maxHeight;
        SpikeBase(BlockPos base, int maxHeight) {
            this.base = base;
            this.maxHeight = maxHeight;
        }
    }

    private static final class SpikeCast {
        final UUID owner;
        final ResourceKey<Level> worldKey;

        final Vec3 startXZ;
        final Vec3 targetXZ;

        final int travelTicks;
        final int seed;

        final int yHint;

        int age;
        boolean spawned;
        int currentHeight;
        int riseTick;

        final List<SpikeBase> spikes = new ArrayList<>();

        SpikeCast(UUID owner, ResourceKey<Level> worldKey, Vec3 startXZ, Vec3 targetXZ,
                  int travelTicks, int seed, int yHint) {
            this.owner = owner;
            this.worldKey = worldKey;
            this.startXZ = startXZ;
            this.targetXZ = targetXZ;
            this.travelTicks = travelTicks;
            this.seed = seed;
            this.yHint = yHint;

            this.age = 0;
            this.spawned = false;
            this.currentHeight = 0;
            this.riseTick = 0;
        }
    }

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        Vec3 look = player.getViewVector(1.0f);

        Vec3 dirXZ = new Vec3(look.x, 0.0, look.z);
        if (dirXZ.lengthSqr() < 1.0e-6) {
            Direction f = player.getDirection();
            dirXZ = new Vec3(f.getStepX(), 0.0, f.getStepZ());
        }
        dirXZ = dirXZ.normalize();

        Vec3 maxXZ = new Vec3(player.getX(), 0.0, player.getZ()).add(dirXZ.scale(SPIKES_RANGE));

        BlockHitResult bhr = raycastBlock(player);

        Vec3 hitPos;
        int yHint;

        if (bhr.getType() == HitResult.Type.BLOCK && bhr.getDirection() != Direction.DOWN) {
            hitPos = bhr.getLocation();
            yHint = Mth.floor(hitPos.y);
        } else {
            hitPos = new Vec3(maxXZ.x, player.getY(), maxXZ.z);
            yHint = player.getBlockY();
        }

        int tx = Mth.floor(hitPos.x);
        int tz = Mth.floor(hitPos.z);

        Vec3 start = new Vec3(player.getX(), 0.0, player.getZ());
        Vec3 target = new Vec3(tx + 0.5, 0.0, tz + 0.5);

        double dist = Math.sqrt(start.distanceToSqr(target));
        int travel = (int) Math.ceil(dist / SPIKES_TRAVEL_SPEED);
        travel = Mth.clamp(travel, SPIKES_TRAVEL_MIN_TICKS, SPIKES_TRAVEL_MAX_TICKS);

        int seed = (int) (w.getGameTime() ^ player.getUUID().getLeastSignificantBits());
        SPIKE_CASTS.add(new SpikeCast(player.getUUID(), w.dimension(), start, target, travel, seed, yHint));

        w.playSound(null, player.blockPosition(), ModSounds.SPIKECAST.get(),
                player.getSoundSource(), 0.7f, 1.00f);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickSpikesWorld(ServerLevel w) {
        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();
        Long last = SPIKES_LAST_TICK.get(key);
        if (last != null && last == now) return;
        SPIKES_LAST_TICK.put(key, now);

        if (SPIKE_CASTS.isEmpty()) return;

        Iterator<SpikeCast> it = SPIKE_CASTS.iterator();
        while (it.hasNext()) {
            SpikeCast sc = it.next();

            if (!sc.worldKey.equals(key)) continue;

            if (sc.age < sc.travelTicks) {
                float t = sc.age / (float) sc.travelTicks;
                spawnGroundTrail(w, sc.startXZ, sc.targetXZ, t, sc.seed, sc.yHint);
                sc.age++;
                continue;
            }

            if (!sc.spawned) {
                buildSpikeBases(w, sc, sc.yHint);

                sc.currentHeight = 1;
                growAllSpikesToHeight(w, sc, sc.currentHeight);

                w.playSound(null, BlockPos.containing(sc.targetXZ.x, w.getMinBuildHeight(), sc.targetXZ.z),
                        SoundEvents.GLASS_PLACE, SoundSource.BLOCKS,
                        0.9f, 1.2f);

                sc.spawned = true;
                sc.riseTick = 0;
                continue;
            }

            if (sc.currentHeight >= SPIKES_MAX_HEIGHT) {
                it.remove();
                continue;
            }

            sc.riseTick++;
            if (sc.riseTick < SPIKES_RISE_INTERVAL) continue;
            sc.riseTick = 0;

            sc.currentHeight++;
            growAllSpikesToHeight(w, sc, sc.currentHeight);

            w.playSound(null, BlockPos.containing(sc.targetXZ.x, w.getMinBuildHeight(), sc.targetXZ.z),
                    SoundEvents.GLASS_HIT, SoundSource.BLOCKS,
                    0.75f, 1.35f);
        }
    }

    private static void spawnGroundTrail(ServerLevel w, Vec3 startXZ, Vec3 targetXZ,
                                         float progress, int seed, int yHint) {

        double sx = startXZ.x;
        double sz = startXZ.z;
        double ex = targetXZ.x;
        double ez = targetXZ.z;

        double fx = Mth.lerp(progress, sx, ex);
        double fz = Mth.lerp(progress, sz, ez);

        double back = 0.18;
        double px0 = Mth.lerp(Mth.clamp(progress - back, 0.0f, 1.0f), sx, ex);
        double pz0 = Mth.lerp(Mth.clamp(progress - back, 0.0f, 1.0f), sz, ez);

        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double a = i / (double) steps;
            double x = Mth.lerp(a, px0, fx);
            double z = Mth.lerp(a, pz0, fz);

            int bx = Mth.floor(x);
            int bz = Mth.floor(z);

            BlockPos surf = findSurfaceAirAboveSolid(w, bx, bz, yHint, false); // do NOT freeze water for trail
            int y = surf.getY();

            double jx = ((seed * 31L + i * 17L) % 100) / 100.0 - 0.5;
            double jz = ((seed * 13L + i * 29L) % 100) / 100.0 - 0.5;

            w.sendParticles(
                    SPIKE_TRAIL_DUST,
                    x + (jx * 0.08),
                    y + 0.06,
                    z + (jz * 0.08),
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );

            if ((i & 1) == 0) {
                w.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.08, z, 1, 0.05, 0.01, 0.05, 0.0);
            }
        }
    }

    private static void buildSpikeBases(ServerLevel w, SpikeCast sc, int yHint) {
        Random rand = new Random(sc.seed);

        int cx = Mth.floor(sc.targetXZ.x);
        int cz = Mth.floor(sc.targetXZ.z);

        HashSet<Long> used = new HashSet<>();

        BlockPos centerBase = findBestSpikeBase(w, cx, cz, yHint);
        if (centerBase != null) {
            sc.spikes.add(new SpikeBase(centerBase, pickSpikeHeight(rand)));
            used.add((((long) centerBase.getX()) << 32) ^ (centerBase.getZ() & 0xffffffffL));
        }

        int attempts = SPIKES_COUNT * 4;
        for (int a = 0; a < attempts && sc.spikes.size() < SPIKES_COUNT; a++) {
            int dx = (rand.nextInt(3) - 1) + (rand.nextInt(3) - 1);
            int dz = (rand.nextInt(3) - 1) + (rand.nextInt(3) - 1);

            dx = Mth.clamp(dx, -SPIKES_SPREAD_RADIUS, SPIKES_SPREAD_RADIUS);
            dz = Mth.clamp(dz, -SPIKES_SPREAD_RADIUS, SPIKES_SPREAD_RADIUS);

            int x = cx + dx;
            int z = cz + dz;

            BlockPos base = findBestSpikeBase(w, x, z, yHint);
            if (base == null) continue;

            long key = (((long) base.getX()) << 32) ^ (base.getZ() & 0xffffffffL);
            if (!used.add(key)) continue;

            sc.spikes.add(new SpikeBase(base, pickSpikeHeight(rand)));
        }

        if (sc.spikes.isEmpty()) {
            BlockPos fallback = findSurfaceAirAboveSolid(w, cx, cz, yHint, true);
            sc.spikes.add(new SpikeBase(fallback, SPIKES_MAX_HEIGHT));
        }
    }

    private static int pickSpikeHeight(Random rand) {
        float r = rand.nextFloat();
        if (r < 0.60f) return 3;
        if (r < 0.88f) return 2;
        return 1;
    }

    private static BlockPos findBestSpikeBase(ServerLevel w, int x, int z, int yHint) {
        BlockPos best = null;
        int bestD2 = Integer.MAX_VALUE;

        for (int dx = -SPIKES_TERRAIN_SEARCH; dx <= SPIKES_TERRAIN_SEARCH; dx++) {
            for (int dz = -SPIKES_TERRAIN_SEARCH; dz <= SPIKES_TERRAIN_SEARCH; dz++) {
                int xx = x + dx;
                int zz = z + dz;

                BlockPos candidate = findSurfaceAirAboveSolid(w, xx, zz, yHint, true); // freeze water surface for spike bases

                int d2 = dx * dx + dz * dz;
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Highly optimized ground-finder that prevents tunneling through the earth
     * when checking over blocks like slabs or snow layers.
     */
    private static BlockPos findSurfaceAirAboveSolid(ServerLevel w, int x, int z, int yHint, boolean freezeWaterSurface) {
        int startY = Mth.clamp(yHint + 3, w.getMinBuildHeight() + 2, w.getHeight() - 2);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, startY, z);

        boolean startedInSolid = false;
        BlockState initial = w.getBlockState(m);
        if (!initial.getFluidState().isEmpty() || (!initial.isAir() && !initial.getCollisionShape(w, m).isEmpty())) {
            startedInSolid = true;
        }

        boolean foundAir = !startedInSolid;

        for (int i = 0; i < 80 && m.getY() > w.getMinBuildHeight() + 2; i++) {
            BlockState hereState = w.getBlockState(m);
            BlockPos below = m.below();
            BlockState belowState = w.getBlockState(below);

            boolean hereIsFluid = !hereState.getFluidState().isEmpty();
            boolean hereHasCollision = !hereState.isAir() && !hereState.getCollisionShape(w, m).isEmpty();
            boolean hereOk = !hereIsFluid && !hereHasCollision;

            if (!foundAir) {
                // If we started inside a block (e.g. cave ceiling), wait until we pop out into the air
                if (hereOk) {
                    foundAir = true;
                }
            }

            if (foundAir) {
                if (!hereOk) {
                    // We hit a solid/fluid block after falling through the air. Stop immediately so we don't tunnel!
                    boolean groundWaterOk = freezeWaterSurface && isStillWater(hereState);
                    if (groundWaterOk) {
                        freezeStillWaterToFrostedIce(w, m);
                    }
                    return m.above().immutable();
                }

                // We are in air. Check if block below is a solid floor.
                boolean belowSolidOk = belowState.isFaceSturdy(w, below, Direction.UP);
                boolean belowWaterOk = freezeWaterSurface && isStillWater(belowState);

                if (belowSolidOk || belowWaterOk) {
                    if (belowWaterOk) {
                        freezeStillWaterToFrostedIce(w, below);
                    }
                    return m.immutable();
                }
            }

            m.move(Direction.DOWN);
        }

        // Fallback if no valid air->ground transition was found in 80 blocks
        if (foundAir) {
            int top = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.min(top, startY);
            BlockPos fallback = new BlockPos(x, y, z);
            if (freezeWaterSurface && isStillWater(w.getBlockState(fallback.below()))) {
                freezeStillWaterToFrostedIce(w, fallback.below());
            }
            return fallback;
        } else {
            // Trapped completely in solid rock
            return new BlockPos(x, yHint, z);
        }
    }

    private static boolean isStillWater(BlockState s) {
        return s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource();
    }

    private static void freezeStillWaterToFrostedIce(ServerLevel w, BlockPos pos) {
        // only freeze if it is water source right now
        BlockState s = w.getBlockState(pos);
        if (!isStillWater(s)) return;

        w.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 2);
        // frosted ice melts naturally
        w.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(w.random, 60, 120));

        w.sendParticles(ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                6, 0.22, 0.10, 0.22, 0.0);
    }

    private static void growAllSpikesToHeight(ServerLevel w, SpikeCast sc, int height) {
        for (SpikeBase sb : sc.spikes) {
            int desired = Math.min(height, sb.maxHeight);
            if (desired <= 0) continue;

            setSpikeColumnHeight(w, sb.base, desired);

            if (desired != height) continue;

            BlockPos top = sb.base.above(desired - 1);

            w.sendParticles(
                    SPIKE_PUFF_DUST,
                    top.getX() + 0.5, top.getY() + 0.35, top.getZ() + 0.5,
                    18,
                    0.22, 0.25, 0.22,
                    0.02
            );
            w.sendParticles(ParticleTypes.SNOWFLAKE,
                    top.getX() + 0.5, top.getY() + 0.45, top.getZ() + 0.5,
                    10, 0.25, 0.20, 0.25, 0.0
            );

            hitEntitiesForSpikeStep(w, sc.owner, top);
        }
    }

    private static void setSpikeColumnHeight(ServerLevel w, BlockPos base, int height) {
        for (int i = 0; i < height; i++) {
            BlockPos p = base.above(i);
            if (!w.getBlockState(p).getFluidState().isEmpty()) return;
        }

        for (int i = 0; i < height; i++) {
            BlockPos p = base.above(i);
            BlockState existing = w.getBlockState(p);

            boolean replaceable = existing.isAir()
                    || (existing.getCollisionShape(w, p).isEmpty() && existing.getFluidState().isEmpty());

            if (!replaceable && !existing.is(ModBlocks.ICE_SPIKE.get())) {
                return;
            }
        }

        if (height == 1) {
            setSpikeState(w, base, DripstoneThickness.TIP);
            return;
        }

        if (height == 2) {
            setSpikeState(w, base, DripstoneThickness.FRUSTUM);
            setSpikeState(w, base.above(1), DripstoneThickness.TIP);
            return;
        }

        setSpikeState(w, base, DripstoneThickness.BASE);
        setSpikeState(w, base.above(1), DripstoneThickness.FRUSTUM);
        setSpikeState(w, base.above(2), DripstoneThickness.TIP);
    }

    private static void setSpikeState(ServerLevel w, BlockPos pos, DripstoneThickness th) {
        BlockState s = ModBlocks.ICE_SPIKE.get().defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, th);

        w.setBlock(pos, s, 2);
    }

    private static void hitEntitiesForSpikeStep(ServerLevel w, UUID owner, BlockPos top) {
        Entity ownerEnt = w.getEntity(owner);
        ServerPlayer caster = (ownerEnt instanceof ServerPlayer sp) ? sp : null;
        if (caster == null) return;

        AABB box = new AABB(top).inflate(0.9, 1.6, 0.9);
        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        int nowTick = (int) (w.getGameTime() & 0x7fffffff);

        for (LivingEntity e : hits) {
            IceVictimState state = getVictimState(e);
            if (state.lastSpikeHitTick == nowTick) continue;
            state.lastSpikeHitTick = nowTick;

            // pass attribution
            e.hurt(ModDamageTypes.iceSpike(w, caster), SPIKES_DAMAGE);

            Vec3 v = e.getDeltaMovement();
            double newY = Math.min(SPIKES_MAX_Y_VEL, Math.max(v.y, SPIKES_KNOCKUP_Y));
            e.setDeltaMovement(v.x, newY, v.z);
            e.hasImpulse = true;
            e.fallDistance = 0.0f;

            shatterOrFreeze(caster, e, SPIKES_FREEZE_STACKS);
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final long SECONDARY_COOLDOWN_MS = 25_000;

    private static final int BEAM_CHARGE_TICKS = 22;
    private static final int BEAM_FIRE_TICKS   = 125;

    private static final double BEAM_RANGE = 34.0;
    private static final double BEAM_RADIUS = 0.75;

    private static final int BEAM_APPLY_EVERY = 2;
    private static final int BEAM_STACKS_PER_APPLY = 5;
    private static final int BEAM_SLOW_AMP = 1;
    private static final int BEAM_SLOW_TICKS = 12;

    private static final int BEAM_DAMAGE_EVERY = 2;
    private static final float BEAM_DAMAGE = 0.75f;

    private static final int BEAM_PARTICLE_DENSITY = 10;
    private static final double BEAM_SPIRAL_RADIUS = 0.15;

    // Beam collision + terrain effects
    private static final double BEAM_BLOCK_EPS = 0.12;     // pull beam endpoint out of the block a bit
    private static final int BEAM_SNOW_PLACE_EVERY = 2;    // how often we try to leave snow at floor impact
    private static final int BEAM_WATER_FREEZE_EVERY = 2;  // how often we try to freeze water along the beam
    private static final int BEAM_MAX_WATER_FREEZES_PER_TICK = 6;

    @Override
    public void activateSecondary(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        if (state.beamChargeTicks > 0) return;
        if (state.beamFireTicks > 0) return;

        state.beamChargeTicks = BEAM_CHARGE_TICKS;

        ServerLevel w = player.serverLevel();
        w.playSound(null, player.blockPosition(),
                ModSounds.ICEBEAMCHARGE.get(),
                player.getSoundSource(),
                0.8f, 1.00f);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickBeam(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        IceCasterState state = getCasterState(player);

        // CHARGING
        if (state.beamChargeTicks > 0) {
            state.beamChargeTicks--;

            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(0.0, v.y, 0.0);
            player.hasImpulse = true;
            player.setSprinting(false);

            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 4, true, false));
            spawnBeamChargeParticles(w, player);

            if (state.beamChargeTicks <= 0) {
                state.beamFireTicks = BEAM_FIRE_TICKS;

                w.playSound(null, player.blockPosition(),
                        ModSounds.ICEBEAMLOOP.get(),
                        player.getSoundSource(),
                        0.9f, 1.55f);
            }
            return;
        }

        // FIRING
        if (state.beamFireTicks > 0) {
            state.beamFireTicks--;

            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(0.0, v.y, 0.0);
            player.hasImpulse = true;
            player.setSprinting(false);

            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, true, false));

            Vec3 dir = player.getViewVector(1.0f).normalize();
            Vec3 muzzle = getBeamMuzzlePos(player, dir);
            Vec3 maxEnd = muzzle.add(dir.scale(BEAM_RANGE));

            // block collision
            BlockHitResult blockHit = raycastBlocks(w, player, muzzle, maxEnd);
            Vec3 end = (blockHit.getType() == HitResult.Type.BLOCK)
                    ? blockHit.getLocation().subtract(dir.scale(BEAM_BLOCK_EPS))
                    : maxEnd;

            // particles
            spawnBeamLineParticles(w, muzzle, end);

            // leave snow
            if (blockHit.getType() == HitResult.Type.BLOCK && (player.tickCount % BEAM_SNOW_PLACE_EVERY) == 0) {
                tryLeaveSnowAtBeamImpact(w, blockHit);
            }

            // freeze water
            if ((player.tickCount % BEAM_WATER_FREEZE_EVERY) == 0) {
                freezeWaterAlongBeam(w, muzzle, end);
            }

            // apply effects
            if ((player.tickCount % BEAM_APPLY_EVERY) == 0) {
                applyBeamToEntities(w, player, muzzle, end);
            }

            // play sounds
            if ((player.tickCount % 15) == 0) {
                w.playSound(null, player.blockPosition(),
                        ModSounds.ICEBEAMLOOP.get(),
                        player.getSoundSource(),
                        0.9f, 1.05f);
            }

            // DPS
            if ((player.tickCount % BEAM_DAMAGE_EVERY) == 0) {
                applyBeamDamage(w, player, muzzle, end);
            }

            if (state.beamFireTicks <= 0) {
                w.playSound(null, player.blockPosition(),
                        SoundEvents.GLASS_HIT,
                        player.getSoundSource(),
                        0.65f, 1.8f);
            }
        }
    }

    private static BlockHitResult raycastBlocks(ServerLevel w, Entity caster, Vec3 start, Vec3 end) {
        return w.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster
        ));
    }

    private static void tryLeaveSnowAtBeamImpact(ServerLevel w, BlockHitResult hit) {
        // “colliding with floor” means we hit the UP face of a block (top surface).
        if (hit.getDirection() != Direction.UP) return;

        BlockPos ground = hit.getBlockPos();
        BlockPos place = ground.above();

        // Only place snow if it's air and the block below can support it
        BlockState below = w.getBlockState(ground);
        BlockState at = w.getBlockState(place);

        boolean placeOk = at.isAir() && below.isFaceSturdy(w, ground, Direction.UP);
        if (!placeOk) return;

        w.setBlock(place, Blocks.SNOW.defaultBlockState(), 2);

        // tiny puff so it feels responsive
        w.sendParticles(ParticleTypes.SNOWFLAKE,
                place.getX() + 0.5, place.getY() + 0.05, place.getZ() + 0.5,
                8, 0.25, 0.03, 0.25, 0.0);
    }

    private static void freezeWaterAlongBeam(ServerLevel w, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.25) return;

        Vec3 dir = delta.scale(1.0 / len);

        // sample roughly every ~0.75 blocks
        double step = 0.75;
        int samples = Mth.clamp((int) (len / step), 3, 40);

        int froze = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= samples; i++) {
            if (froze >= BEAM_MAX_WATER_FREEZES_PER_TICK) break;

            Vec3 p = start.add(dir.scale(i * step));
            m.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));

            // check the block we are inside + a tiny neighborhood (helps “touching” water)
            for (int dx = -1; dx <= 1 && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; dx++) {
                for (int dy = -1; dy <= 1 && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; dy++) {
                    for (int dz = -1; dz <= 1 && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; dz++) {
                        BlockPos pos = m.offset(dx, dy, dz);
                        BlockState s = w.getBlockState(pos);

                        // “solid water blocks” = water source blocks (still)
                        if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) {
                            // use frosted ice (like frost walker) so it melts back naturally
                            w.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 2);
                            w.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(w.random, 60, 120));

                            w.sendParticles(ParticleTypes.SNOWFLAKE,
                                    pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5,
                                    6, 0.25, 0.15, 0.25, 0.0);

                            froze++;
                        }
                    }
                }
            }
        }
    }

    private static void spawnBeamChargeParticles(ServerLevel w, ServerPlayer player) {
        Vec3 dir = player.getViewVector(1.0f).normalize();
        Vec3 muzzle = getBeamMuzzlePos(player, dir);

        int points = 14;
        double r = 0.22;

        for (int i = 0; i < points; i++) {
            double a = (player.tickCount * 0.45) + (i * (Math.PI * 2.0 / points));
            double x = muzzle.x + Math.cos(a) * r;
            double z = muzzle.z + Math.sin(a) * r;
            double y = muzzle.y + (w.random.nextDouble() - 0.5) * 0.12;

            w.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.0);

            if ((i % 3) == 0) {
                w.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if ((i & 1) == 0) {
                w.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void spawnBeamLineParticles(ServerLevel w, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.001) return;

        Vec3 dir = delta.scale(1.0 / len);

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = up.cross(dir);
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();

        Vec3 up2 = dir.cross(right).normalize();

        int steps = Mth.clamp((int) (len * BEAM_PARTICLE_DENSITY), 24, 140);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = start.add(delta.scale(t));

            // core shimmer line
            w.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);

            // spiral around beam
            double ang = (w.getGameTime() * 0.45) + (i * 0.65);
            double rx = Math.cos(ang) * BEAM_SPIRAL_RADIUS;
            double ry = Math.sin(ang) * BEAM_SPIRAL_RADIUS;

            Vec3 swirl = p.add(right.scale(rx)).add(up2.scale(ry));
            w.sendParticles(ParticleTypes.SNOWFLAKE, swirl.x, swirl.y, swirl.z, 1, 0.0, 0.0, 0.0, 0.0);

            // sparkles
            if ((i % 10) == 0) {
                w.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
            if ((i % 14) == 0) {
                w.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void applyBeamToEntities(ServerLevel w, ServerPlayer caster, Vec3 start, Vec3 end) {
        AABB scan = new AABB(start, end).inflate(BEAM_RADIUS, BEAM_RADIUS, BEAM_RADIUS);

        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e.isAlive() && e != caster);

        double w2 = BEAM_RADIUS * BEAM_RADIUS;

        for (LivingEntity e : hits) {
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d2 = distSqPointToSegment(p, start, end);
            if (d2 > w2) continue;

            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BEAM_SLOW_TICKS, BEAM_SLOW_AMP, true, false));
            applyBeamDrag(e);

            applyFreezePoints(caster, e, BEAM_STACKS_PER_APPLY);
        }
    }

    private static void applyBeamDamage(ServerLevel w, ServerPlayer caster, Vec3 start, Vec3 end) {
        AABB scan = new AABB(start, end).inflate(BEAM_RADIUS, BEAM_RADIUS, BEAM_RADIUS);

        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e.isAlive() && e != caster);

        double w2 = BEAM_RADIUS * BEAM_RADIUS;

        for (LivingEntity e : hits) {
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d2 = distSqPointToSegment(p, start, end);
            if (d2 > w2) continue;

            e.hurt(ModDamageTypes.iceBeam(w, caster), BEAM_DAMAGE);
        }
    }

    private static void applyBeamDrag(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();

        double nx = v.x * 0.20;
        double nz = v.z * 0.20;

        double ny = v.y;
        if (ny > 0.12) ny = 0.12;
        ny -= 0.08;
        ny = Math.max(ny, -0.65);

        e.setDeltaMovement(nx, ny, nz);
        e.hasImpulse = true;
        e.fallDistance = 0.0f;
    }

    private static Vec3 getBeamMuzzlePos(ServerPlayer player, Vec3 dir) {
        Vec3 eye = player.getEyePosition();

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = up.cross(dir);
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();

        double side = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) ? 0.24 : -0.24;

        return eye
                .add(dir.scale(0.30))
                .add(0.0, -0.28, 0.0)
                .add(right.scale(side));
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    private static final long ULT_COOLDOWN_MS = 340_000;

    private static final int ULT_DURATION_TICKS = 180;

    private static final double ULT_BLIZZARD_RADIUS = 11.0;
    private static final int ULT_SNOW_PER_TICK = 50;
    private static final int ULT_GUST_PER_TICK = 30;

    // Water freeze
    private static final int ULT_FROST_RADIUS = 4;
    private static final int ULT_FROST_EVERY = 2; // do it every 2 ticks to reduce cost
    private static final int ULT_FROST_MAX_PER_TICK = 10;

    // Ground snow
    private static final int ULT_SNOW_COVER_EVERY = 5;          // try every tick
    private static final int ULT_SNOW_COVER_ATTEMPTS = 45;      // how many random placements per tick
    private static final int ULT_SNOW_MAX_LAYERS = 2;           // vanilla max

    // Shockwave
    private static final int ULT_WAVE_INTERVAL = 16;
    private static final double ULT_WAVE_SPEED = 1.45;
    private static final double ULT_WAVE_MAX_RADIUS = 14.0;
    private static final double ULT_WAVE_THICKNESS = 0.80;

    private static final double ULT_WAVE_HEIGHT_OFFSET = 0.10;
    private static final double ULT_WAVE_JUMP_CLEARANCE = 0.55;

    private static final float ULT_WAVE_DAMAGE = 5.5f;
    private static final double ULT_WAVE_KB = 0.25;
    private static final double ULT_WAVE_UP = 0.07;
    private static final int ULT_WAVE_FREEZE_STACKS = 35;
    private static final int ULT_WAVE_SLOW_TICKS = 20;
    private static final int ULT_WAVE_SLOW_AMP = 1;

    // egg
    private static final double ULT_SNOWMAN_CHANCE = 0.002; // chance per tick

    // Extra FX
    private static final DustParticleOptions ULT_BLUE_DUST =
            new DustParticleOptions(new Vector3f(0.20f, 0.55f, 1.00f), 0.90f);

    private static final DustParticleOptions ULT_SHIMMER_DUST =
            new DustParticleOptions(new Vector3f(0.70f, 0.95f, 1.00f), 0.55f);

    private static final List<UltWave> ULT_WAVES = new ArrayList<>();
    private static final Map<ResourceKey<Level>, Long> ULTW_LAST_TICK = new HashMap<>();
    private static int ULT_WAVE_ID_SEQ = 1;

    private static int nextUltWaveId() {
        int id = ULT_WAVE_ID_SEQ++;
        if (ULT_WAVE_ID_SEQ > 2_000_000_000) ULT_WAVE_ID_SEQ = 1;
        return id;
    }

    private static final class UltWave {
        final int id;
        final UUID owner;
        final ResourceKey<Level> worldKey;

        final Vec3 centerXZ;
        final double waveY;
        double radius;
        final double maxRadius;

        UltWave(int id, UUID owner, ResourceKey<Level> worldKey, Vec3 centerXZ, double waveY, double radius, double maxRadius) {
            this.id = id;
            this.owner = owner;
            this.worldKey = worldKey;
            this.centerXZ = centerXZ;
            this.waveY = waveY;
            this.radius = radius;
            this.maxRadius = maxRadius;
        }
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        state.ultActiveTicks = ULT_DURATION_TICKS;
        state.ultPulseTicks = 1;

        ServerLevel w = player.serverLevel();
        w.playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                player.getSoundSource(),
                0.9f, 0.75f);
        playBlizzardLoop(w, player);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickUltimate(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        if (state.ultActiveTicks <= 0) return;
        state.ultActiveTicks--;

        ServerLevel w = player.serverLevel();

        // 1. Blizzard Global Effects
        spawnBlizzard(w, player);

        // 2. Entity effects
        spawnSnowAroundEntities(w, player);

        // 3. Ground snow accumulation
        if ((player.tickCount % ULT_SNOW_COVER_EVERY) == 0) {
            spreadSnowCover(w, player);
        }

        // 4. Random Snowman Building
        if (w.random.nextDouble() < ULT_SNOWMAN_CHANCE) {
            tryStartSnowmanBuild(w, player, state);
        }
        tickActiveSnowmanBuilds(w, state);

        // 5. Water freeze around caster
        if ((player.tickCount % ULT_FROST_EVERY) == 0) {
            freezeWaterAroundCaster(w, player);
        }

        // 6. Pulse Waves & Loop Sound
        if (state.ultPulseTicks > 0) {
            state.ultPulseTicks--;
            if (state.ultPulseTicks <= 0) {
                state.ultPulseTicks = ULT_WAVE_INTERVAL;
                spawnUltWave(w, player);
                w.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 0.8f, 0.85f);
            }
        }

        if (player.tickCount % 30 == 0) {
            playBlizzardLoop(w, player);
        }
        tickUltWavesWorld(w);
    }

    private static void freezeWaterAroundCaster(ServerLevel w, ServerPlayer caster) {
        BlockPos center = caster.blockPosition();
        int froze = 0;

        // sample a square, but early-exit
        for (int dx = -ULT_FROST_RADIUS; dx <= ULT_FROST_RADIUS && froze < ULT_FROST_MAX_PER_TICK; dx++) {
            for (int dz = -ULT_FROST_RADIUS; dz <= ULT_FROST_RADIUS && froze < ULT_FROST_MAX_PER_TICK; dz++) {
                // circular-ish
                if ((dx * dx + dz * dz) > ULT_FROST_RADIUS * ULT_FROST_RADIUS) continue;

                BlockPos pos = center.offset(dx, 0, dz);

                // Frost Walker targets water at feet level (usually just below)
                // Try y-1..y+1 to be forgiving on slopes
                for (int dy = -1; dy <= 1 && froze < ULT_FROST_MAX_PER_TICK; dy++) {
                    BlockPos p = pos.offset(0, dy, 0);

                    BlockState s = w.getBlockState(p);
                    if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) {
                        w.setBlock(p, Blocks.FROSTED_ICE.defaultBlockState(), 2);
                        w.scheduleTick(p, Blocks.FROSTED_ICE, Mth.nextInt(w.random, 60, 120));

                        // tiny sparkle
                        w.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5,
                                4, 0.20, 0.10, 0.20, 0.0);
                        if ((froze & 1) == 0) {
                            w.sendParticles(ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5,
                                    1, 0.02, 0.02, 0.02, 0.0);
                        }

                        froze++;
                    }
                }
            }
        }
    }

    private static void spawnBlizzard(ServerLevel w, ServerPlayer caster) {
        Vec3 c = caster.position();

        for (int i = 0; i < ULT_SNOW_PER_TICK; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * ULT_BLIZZARD_RADIUS;
            double a = w.random.nextDouble() * Math.PI * 2.0;

            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 6.0 + w.random.nextDouble() * 3.0;

            w.sendParticles(ParticleTypes.SNOWFLAKE,
                    x, y, z,
                    1,
                    0.25, 0.15, 0.25,
                    0.00);

            // extra shimmer sometimes
            if ((i % 12) == 0) {
                w.sendParticles(ParticleTypes.END_ROD, x, y - 0.4, z, 1, 0, 0, 0, 0.0);
            }
        }

        for (int i = 0; i < ULT_GUST_PER_TICK; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * (ULT_BLIZZARD_RADIUS * 0.9);
            double a = w.random.nextDouble() * Math.PI * 2.0;

            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 1.0 + w.random.nextDouble() * 2.0;

            w.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 1, 0.22, 0.18, 0.22, 0.00);

            // blue “cold” tint in the air
            if ((i & 3) == 0) {
                w.sendParticles(ULT_BLUE_DUST, x, y, z, 1, 0.10, 0.08, 0.10, 0.0);
            }
        }
    }

    private static void spreadSnowCover(ServerLevel w, ServerPlayer caster) {
        Vec3 c = caster.position();

        for (int i = 0; i < ULT_SNOW_COVER_ATTEMPTS; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * ((int)Math.ceil(ULT_BLIZZARD_RADIUS));
            double a = w.random.nextDouble() * (Math.PI * 2.0);

            int x = Mth.floor(c.x + Math.cos(a) * r);
            int z = Mth.floor(c.z + Math.sin(a) * r);

            BlockPos place = findSurfaceAirAboveSolid(w, x, z, caster.getBlockY(), true);

            if (w.isOutsideBuildHeight(place)) continue;

            BlockPos belowPos = place.below();
            BlockState below = w.getBlockState(belowPos);
            BlockState at = w.getBlockState(place);

            if (!below.isFaceSturdy(w, belowPos, Direction.UP)) continue;

            if (at.is(Blocks.SNOW)) {
                int layers = at.getValue(SnowLayerBlock.LAYERS);
                if (layers < ULT_SNOW_MAX_LAYERS) {
                    w.setBlock(place, at.setValue(SnowLayerBlock.LAYERS, layers + 1), 2);
                }
                continue;
            }

            // now only target air
            if (!at.isAir()) continue;

            w.setBlock(place, Blocks.SNOW.defaultBlockState(), 2);

            if ((i % 10) == 0) {
                w.sendParticles(ParticleTypes.SNOWFLAKE,
                        place.getX() + 0.5, place.getY() + 0.05, place.getZ() + 0.5,
                        6, 0.22, 0.03, 0.22, 0.0);
            }
        }
    }

    private static void spawnUltWave(ServerLevel w, ServerPlayer caster) {
        // anchored to ground
        BlockPos groundAir = findSurfaceAirAboveSolid(w, caster.getBlockX(), caster.getBlockZ(), caster.getBlockY(), true);
        double waveY = groundAir.getY() + ULT_WAVE_HEIGHT_OFFSET;

        UltWave wave = new UltWave(
                nextUltWaveId(),
                caster.getUUID(),
                w.dimension(),
                new Vec3(caster.getX(), 0.0, caster.getZ()),
                waveY,
                0.8,
                ULT_WAVE_MAX_RADIUS
        );
        ULT_WAVES.add(wave);
    }

    private static void tickUltWavesWorld(ServerLevel w) {
        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();

        Long last = ULTW_LAST_TICK.get(key);
        if (last != null && last == now) return;
        ULTW_LAST_TICK.put(key, now);

        if (ULT_WAVES.isEmpty()) return;

        Iterator<UltWave> it = ULT_WAVES.iterator();
        while (it.hasNext()) {
            UltWave wave = it.next();
            if (!wave.worldKey.equals(key)) continue;

            wave.radius += ULT_WAVE_SPEED;

            spawnWaveRingParticles(w, wave);
            applyWaveHits(w, wave);

            if (wave.radius >= wave.maxRadius) {
                it.remove();
            }
        }
    }

    private static void spawnWaveRingParticles(ServerLevel w, UltWave wave) {
        double r = wave.radius;
        int points = Mth.clamp((int) (r * 14.0), 28, 180);

        double y = wave.waveY;

        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * (Math.PI * 2.0);

            double x = wave.centerXZ.x + Math.cos(a) * r;
            double z = wave.centerXZ.z + Math.sin(a) * r;

            w.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.01, 0.02, 0.0);

            // dust
            if ((i % 5) == 0) {
                w.sendParticles(ULT_BLUE_DUST, x, y + 0.02, z, 1, 0.02, 0.01, 0.02, 0.0);
            }
            if ((i % 9) == 0) {
                w.sendParticles(ULT_SHIMMER_DUST, x, y + 0.06, z, 1, 0.02, 0.01, 0.02, 0.0);
            }

            if ((i % 8) == 0) {
                w.sendParticles(ParticleTypes.END_ROD, x, y + 0.05, z, 1, 0, 0, 0, 0.0);
            }
            if ((i % 11) == 0) {
                w.sendParticles(ParticleTypes.ENCHANT, x, y + 0.08, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void applyWaveHits(ServerLevel w, UltWave wave) {
        Vec3 c = wave.centerXZ;
        double scanR = wave.radius + ULT_WAVE_THICKNESS + 1.25;

        AABB scan = new AABB(c.x - scanR, wave.waveY - 3.0, c.z - scanR,
                c.x + scanR, wave.waveY + 4.0, c.z + scanR);

        // Added check to ignore Snow Golems in the scan filter
        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e.isAlive() &&
                        !e.getUUID().equals(wave.owner) &&
                        !(e instanceof SnowGolem));

        for (LivingEntity e : hits) {
            double eTop = e.getY() + e.getBbHeight();
            if (eTop < wave.waveY - 0.10) continue;
            if (e.getY() > wave.waveY + ULT_WAVE_JUMP_CLEARANCE) continue;

            Vec3 p = e.position();
            double dx = p.x - c.x;
            double dz = p.z - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - wave.radius) > ULT_WAVE_THICKNESS) continue;

            IceVictimState state = getVictimState(e);
            if (state.lastWaveHitId == wave.id) continue;
            state.lastWaveHitId = wave.id;

            Entity ownerEnt = w.getEntity(wave.owner);
            ServerPlayer caster = (ownerEnt instanceof ServerPlayer sp) ? sp : null;

            if (caster != null) {
                e.hurt(ModDamageTypes.iceShockwave(w, caster), ULT_WAVE_DAMAGE);
                shatterOrFreeze(caster, e, ULT_WAVE_FREEZE_STACKS);
            }

            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ULT_WAVE_SLOW_TICKS, ULT_WAVE_SLOW_AMP, true, false));

            Vec3 v = e.getDeltaMovement();
            double inv = (dist < 1.0e-4) ? 0.0 : (1.0 / dist);
            double pushX = dx * inv * ULT_WAVE_KB;
            double pushZ = dz * inv * ULT_WAVE_KB;
            double newY = Math.min(0.55, Math.max(v.y, ULT_WAVE_UP));
            e.setDeltaMovement(v.x + pushX, newY, v.z + pushZ);
            e.hasImpulse = true;
            e.fallDistance = 0.0f;
        }
    }

    private static void playBlizzardLoop(ServerLevel world, ServerPlayer caster) {
        double radius = ULT_BLIZZARD_RADIUS;

        for (ServerPlayer p : world.players()) {
            if (p.distanceToSqr(caster) <= radius * radius) {

                world.playSound(
                        null, // send to all nearby (including the player)
                        p.blockPosition(),
                        ModSounds.BLIZZARDLOOP.get(),
                        p.getSoundSource(),
                        0.3f,
                        0.8f
                );
            }
        }
    }

    private static void spawnSnowAroundEntities(ServerLevel w, ServerPlayer caster) {
        AABB area = caster.getBoundingBox().inflate(ULT_BLIZZARD_RADIUS);
        List<LivingEntity> targets = w.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != caster);

        for (LivingEntity e : targets) {
            Vec3 p = e.position();
            // Blowing gusts swirling around the entity
            for (int i = 0; i < 3; i++) {
                double ox = (w.random.nextDouble() - 0.5) * 1.5;
                double oz = (w.random.nextDouble() - 0.5) * 1.5;
                double oy = w.random.nextDouble() * e.getBbHeight();

                // Wind-blown particles with horizontal velocity
                w.sendParticles(ParticleTypes.SNOWFLAKE, p.x + ox, p.y + oy, p.z + oz, 1, 0.5, 0.1, 0.5, 0.05);

                if (w.random.nextBoolean()) {
                    w.sendParticles(ParticleTypes.WHITE_ASH, p.x + ox, p.y + oy, p.z + oz, 1, 0.2, 0.0, 0.2, 0.02);
                }
            }
        }
    }

    // EGG
    private static void tryStartSnowmanBuild(ServerLevel w, ServerPlayer caster, IceCasterState state) {
        double r = w.random.nextDouble() * (ULT_BLIZZARD_RADIUS - 2);
        double a = w.random.nextDouble() * Math.PI * 2.0;
        int x = Mth.floor(caster.getX() + Math.cos(a) * r);
        int z = Mth.floor(caster.getZ() + Math.sin(a) * r);

        BlockPos pos = findSurfaceAirAboveSolid(w, x, z, caster.getBlockY(), true);
        if (w.getBlockState(pos).isAir()) {
            state.snowmen.add(new SnowmanBuild(pos));
        }
    }

    private static void tickActiveSnowmanBuilds(ServerLevel w, IceCasterState state) {
        Iterator<SnowmanBuild> it = state.snowmen.iterator();

        while (it.hasNext()) {
            SnowmanBuild build = it.next();
            build.delay--;

            if (build.delay <= 0) {
                if (build.step == 1) { // Place bottom snow
                    w.setBlock(build.pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                    w.playSound(null, build.pos, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1f, 1f);
                    build.step = 2;
                    build.delay = 15;
                } else if (build.step == 2) { // Place top snow
                    w.setBlock(build.pos.above(), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                    w.playSound(null, build.pos.above(), SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
                    build.step = 3;
                    build.delay = 15;
                } else if (build.step == 3) { // Place pumpkin (Vanilla triggers golem spawn)
                    w.setBlock(build.pos.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState(), 3);
                    w.sendParticles(ParticleTypes.SNOWFLAKE, build.pos.getX()+0.5, build.pos.getY()+2, build.pos.getZ()+0.5, 20, 0.5, 0.5, 0.5, 0.05);
                    it.remove();
                }
            }
        }
    }

    /* ============================================================
       RAYCAST + GEOMETRY
       ============================================================ */

    private static BlockHitResult raycastBlock(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 end = start.add(look.scale(SPIKES_RANGE));

        return player.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
    }

    private static double distSqPointToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double abLen2 = ab.lengthSqr();
        if (abLen2 < 1.0e-9) return p.distanceToSqr(a);

        double t = (p.subtract(a)).dot(ab) / abLen2;
        t = Mth.clamp(t, 0.0, 1.0);

        Vec3 proj = a.add(ab.scale(t));
        return p.distanceToSqr(proj);
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.ice.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.ice.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.ice.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.ice.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs() { return PRIMARY_COOLDOWN_MS; }
    @Override public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }
    @Override public long getUltimateCooldownMs() { return ULT_COOLDOWN_MS; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.ice.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.ice.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.ice.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.ice.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.ice.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.ice.description.ultimate").getString();
    }
}