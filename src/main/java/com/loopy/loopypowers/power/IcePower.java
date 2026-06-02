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
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import com.loopy.loopypowers.network.payload.*;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class IcePower implements PowerInterface {

    // had to trim down the absurd size of this class :(

    /* ============================================================
       STATE
       ============================================================ */

    private static final Map<UUID, IceCasterState> CASTER_STATES = new HashMap<>();
    private static final Map<UUID, IceVictimState> VICTIM_STATES = new HashMap<>();

    private static class SnowmanBuild {
        int step = 1, delay = 10;
        final BlockPos pos;
        SnowmanBuild(BlockPos pos) { this.pos = pos; }
    }

    private static class IceCasterState {
        int beamChargeTicks = 0, beamFireTicks = 0;
        int ultActiveTicks = 0, ultPulseTicks = 0;
        List<SnowmanBuild> snowmen = new ArrayList<>();
    }

    private static class IceVictimState {
        int freezePoints = 0, freezeDecayTicks = 0, freezeImmuneTicks = 0;
        int lastSpikeHitTick = -1, lastWaveHitId = -1;
        ResourceKey<Level> worldKey;
        IceVictimState(ResourceKey<Level> worldKey) { this.worldKey = worldKey; }
    }

    private static IceCasterState getCasterState(ServerPlayer p) {
        return CASTER_STATES.computeIfAbsent(p.getUUID(), k -> new IceCasterState());
    }
    private static IceVictimState getVictimState(LivingEntity e) {
        ServerLevel w = (ServerLevel) e.level();
        return VICTIM_STATES.computeIfAbsent(e.getUUID(), k -> new IceVictimState(w.dimension()));
    }

    /** Returns false if this world has already been ticked this game-tick — prevents double-ticking per-world logic. */
    private static boolean claimWorldTick(Map<ResourceKey<Level>, Long> seen, ServerLevel w) {
        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();
        if (now == seen.getOrDefault(key, -1L)) return false;
        seen.put(key, now);
        return true;
    }

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("ice_"));
        CASTER_STATES.put(player.getUUID(), new IceCasterState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("ice_"));
        CASTER_STATES.remove(player.getUUID());
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        if (player.getServer() != null) {
            for (ServerLevel w : player.getServer().getAllLevels()) {
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(150), LivingEntity::isAlive)) {
                    IceVictimState state = VICTIM_STATES.get(e.getUUID());
                    if (state != null && state.freezePoints > 0) {
                        state.freezePoints = 0;
                        e.setTicksFrozen(0);
                        e.removeEffect(ModEffects.DEEPFREEZE);
                    }
                }
                SPIKE_CASTS.removeIf(sc -> sc.owner.equals(player.getUUID()));
                ULT_WAVES.removeIf(uw -> uw.owner.equals(player.getUUID()));
            }
        }
    }

    @Override public void onDeath(ServerPlayer player) { onRemove(player); }

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
        if (!PassiveManager.isEnabled(attacker)) return;
        shatterOrFreeze(attacker, target, FRZ_POINTS_MELEE);
    }

    /* ============================================================
       PASSIVE — FREEZE
       ============================================================ */

    private static final int FRZ_MAX_POINTS       = 100;
    private static final int FRZ_STAGE_1          = 10;
    private static final int FRZ_STAGE_2          = 40;
    private static final int FRZ_STAGE_3          = 60;
    private static final int FRZ_STAGE_4          = 80;
    private static final int FRZ_STAGE_5          = 95;
    private static final int FRZ_DECAY_DELAY_TICKS = 20;
    private static final int FRZ_DECAY_STEP_TICKS  = 10;
    private static final int FRZ_DECAY_POINTS_STEP = 5;
    private static final int FRZ_IMMUNE_TICKS      = 100;
    private static final float SHATTER_BONUS_DAMAGE = 12.0f;
    private static final int FRZ_POINTS_MELEE      = 10;

    private static final Map<ResourceKey<Level>, Long> FROZEN_LAST_TICK = new HashMap<>();

    private static int getFreezeStage(int points) {
        if (points < FRZ_STAGE_1) return 0;
        if (points < FRZ_STAGE_2) return 1;
        if (points < FRZ_STAGE_3) return 2;
        if (points < FRZ_STAGE_4) return 3;
        if (points < FRZ_STAGE_5) return 4;
        return 5;
    }

    private static void syncFreezeTimer(LivingEntity e, IceVictimState state) {
        if (state.freezePoints <= 0) {
            e.removeEffect(ModEffects.DEEPFREEZE);
            return;
        }
        int steps = (int) Math.ceil((double) state.freezePoints / FRZ_DECAY_POINTS_STEP);
        int ticksRemaining = state.freezeDecayTicks + Math.max(0, (steps - 1) * FRZ_DECAY_STEP_TICKS);
        e.removeEffect(ModEffects.DEEPFREEZE);
        e.addEffect(new MobEffectInstance(ModEffects.DEEPFREEZE, ticksRemaining, 0, false, false, true));
    }

    private static void applyFreezePoints(ServerPlayer caster, LivingEntity target, int addPoints) {
        if (addPoints <= 0) return;
        IceVictimState state = getVictimState(target);
        if (state.freezeImmuneTicks > 0) return;
        state.freezePoints = Mth.clamp(state.freezePoints + addPoints, 0, FRZ_MAX_POINTS);
        state.freezeDecayTicks = FRZ_DECAY_DELAY_TICKS;
        syncFreezeTimer(target, state);
        spawnFreezeStageParticles(caster.serverLevel(), target, getFreezeStage(state.freezePoints));
    }

    private static void shatterOrFreeze(ServerPlayer caster, LivingEntity target, int freezeStacks) {
        IceVictimState state = getVictimState(target);
        if (state.freezeImmuneTicks > 0) return;

        if (state.freezePoints >= FRZ_STAGE_5) {
            ServerLevel w = caster.serverLevel();
            target.hurt(ModDamageTypes.iceShatter(w, caster), SHATTER_BONUS_DAMAGE);
            doShatterFX(w, target);
            w.playSound(null, target.blockPosition(), ModSounds.SHATTER.get(), SoundSource.PLAYERS, 0.5f, 0.8f);
            CameraShake.shakeNearby(caster, 6, 8, 0.35f);
            clearFreeze(target);
            state.freezeImmuneTicks = FRZ_IMMUNE_TICKS;
        } else {
            applyFreezePoints(caster, target, freezeStacks);
        }
    }

    private static void doShatterFX(ServerLevel w, LivingEntity target) {
        Vec3 p = target.position().add(0, target.getBbHeight() * 0.55, 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new IceShatterPayload(target.getId(), p.x, p.y, p.z));
        w.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK,             SoundSource.PLAYERS, 0.9f, 1.15f);
        w.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9f, 1.55f);
    }

    private static void clearFreeze(LivingEntity e) {
        IceVictimState state = VICTIM_STATES.get(e.getUUID());
        if (state != null) { state.freezePoints = 0; state.freezeDecayTicks = 0; }
        e.setTicksFrozen(0);
        e.removeEffect(ModEffects.DEEPFREEZE);
    }

    private static void tickFrozenWorld(ServerLevel w) {
        if (!claimWorldTick(FROZEN_LAST_TICK, w)) return;
        if (VICTIM_STATES.isEmpty()) return;

        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();
        Iterator<Map.Entry<UUID, IceVictimState>> it = VICTIM_STATES.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, IceVictimState> entry = it.next();
            IceVictimState state = entry.getValue();
            if (!state.worldKey.equals(key)) continue;

            Entity ent = w.getEntity(entry.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            if (state.freezeImmuneTicks > 0) state.freezeImmuneTicks--;

            int points = state.freezePoints;
            if (points <= 0) {
                if (state.freezeImmuneTicks <= 0) it.remove();
                continue;
            }

            if (now % 10 == 0 || !le.hasEffect(ModEffects.DEEPFREEZE)) syncFreezeTimer(le, state);

            applyFreezeStageEffects(w, le, getFreezeStage(points));

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
        final int T = 10;
        spawnFreezeStageParticles(w, e, stage);

        if (stage >= 2) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, T, stage >= 3 ? 1 : 0, true, false));
        if (stage >= 4) e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,       T, 0, true, false));
        if (stage >= 5) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, T, 4, true, false));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,      T, 2, true, false));
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.25, Mth.clamp(v.y, -0.5, 0.5), v.z * 0.25);
            e.hasImpulse = true;
            e.hurtMarked  = true; // sync freeze-drag velocity to all tracking clients
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            e.fallDistance = 0.0f;
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
        }
    }

    private static void spawnFreezeStageParticles(ServerLevel w, LivingEntity e, int stage) {
        if (stage <= 0) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e,
                new IceFreezeStagePayload(e.getId(), stage, w.getGameTime()));
    }

    /* ============================================================
       PRIMARY — ICE SPIKES
       ============================================================ */

    private static final long PRIMARY_COOLDOWN_MS    = 13_000;
    private static final double SPIKES_RANGE         = 12.0;
    private static final int SPIKES_COUNT            = 9;
    private static final int SPIKES_SPREAD_RADIUS    = 1;
    private static final int SPIKES_TERRAIN_SEARCH   = 3;
    private static final int SPIKES_MAX_HEIGHT       = 3;
    private static final int SPIKES_RISE_INTERVAL    = 2;
    private static final double SPIKES_MAX_Y_VEL     = 1.65;
    private static final float SPIKES_DAMAGE         = 8.5f;
    private static final double SPIKES_KNOCKUP_Y     = 0.75;
    private static final int SPIKES_FREEZE_STACKS    = 15;
    private static final double SPIKES_TRAVEL_SPEED  = 0.9;
    private static final int SPIKES_TRAVEL_MIN_TICKS = 6;
    private static final int SPIKES_TRAVEL_MAX_TICKS = 16;

    private static final List<SpikeCast>               SPIKE_CASTS      = new ArrayList<>();
    private static final Map<ResourceKey<Level>, Long> SPIKES_LAST_TICK = new HashMap<>();

    private static final class SpikeBase {
        final BlockPos base;
        final int maxHeight;
        SpikeBase(BlockPos base, int maxHeight) { this.base = base; this.maxHeight = maxHeight; }
    }

    private static final class SpikeCast {
        final UUID owner;
        final ResourceKey<Level> worldKey;
        final Vec3 startXZ, targetXZ;
        final int travelTicks, seed, yHint;
        int age = 0, currentHeight = 0, riseTick = 0;
        boolean spawned = false;
        final List<SpikeBase> spikes = new ArrayList<>();

        SpikeCast(UUID owner, ResourceKey<Level> worldKey, Vec3 startXZ, Vec3 targetXZ,
                  int travelTicks, int seed, int yHint) {
            this.owner = owner; this.worldKey = worldKey;
            this.startXZ = startXZ; this.targetXZ = targetXZ;
            this.travelTicks = travelTicks; this.seed = seed; this.yHint = yHint;
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

        Vec3 eyePos = player.getEyePosition();
        BlockHitResult bhr = player.level().clip(new ClipContext(
                eyePos, eyePos.add(player.getViewVector(1.0f).normalize().scale(SPIKES_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

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
        Vec3 start  = new Vec3(player.getX(), 0.0, player.getZ());
        Vec3 target = new Vec3(tx + 0.5, 0.0, tz + 0.5);

        double dist = Math.sqrt(start.distanceToSqr(target));
        int travel = Mth.clamp((int) Math.ceil(dist / SPIKES_TRAVEL_SPEED), SPIKES_TRAVEL_MIN_TICKS, SPIKES_TRAVEL_MAX_TICKS);
        int seed = (int) (w.getGameTime() ^ player.getUUID().getLeastSignificantBits());

        SPIKE_CASTS.add(new SpikeCast(player.getUUID(), w.dimension(), start, target, travel, seed, yHint));
        w.playSound(null, player.blockPosition(), ModSounds.SPIKECAST.get(), player.getSoundSource(), 0.7f, 1.00f);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickSpikesWorld(ServerLevel w) {
        if (!claimWorldTick(SPIKES_LAST_TICK, w)) return;
        if (SPIKE_CASTS.isEmpty()) return;

        Iterator<SpikeCast> it = SPIKE_CASTS.iterator();
        while (it.hasNext()) {
            SpikeCast sc = it.next();
            if (!sc.worldKey.equals(w.dimension())) continue;

            if (sc.age < sc.travelTicks) {
                Entity ownerEnt = w.getEntity(sc.owner);
                if (ownerEnt instanceof ServerPlayer caster) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                            new IceSpikeTrailPayload(
                                    sc.startXZ.x, sc.startXZ.z,
                                    sc.targetXZ.x, sc.targetXZ.z,
                                    sc.age / (float) sc.travelTicks,
                                    sc.seed, sc.yHint));
                }
                sc.age++;
                continue;
            }

            if (!sc.spawned) {
                buildSpikeBases(w, sc, sc.yHint);
                sc.currentHeight = 1;
                growAllSpikesToHeight(w, sc, sc.currentHeight);
                w.playSound(null, BlockPos.containing(sc.targetXZ.x, w.getMinBuildHeight(), sc.targetXZ.z),
                        SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.9f, 1.2f);
                sc.spawned = true;
                sc.riseTick = 0;
                continue;
            }

            if (sc.currentHeight >= SPIKES_MAX_HEIGHT) { it.remove(); continue; }

            if (++sc.riseTick < SPIKES_RISE_INTERVAL) continue;
            sc.riseTick = 0;
            sc.currentHeight++;
            growAllSpikesToHeight(w, sc, sc.currentHeight);
            w.playSound(null, BlockPos.containing(sc.targetXZ.x, w.getMinBuildHeight(), sc.targetXZ.z),
                    SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.75f, 1.35f);
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
            int dx = Mth.clamp((rand.nextInt(3) - 1) + (rand.nextInt(3) - 1), -SPIKES_SPREAD_RADIUS, SPIKES_SPREAD_RADIUS);
            int dz = Mth.clamp((rand.nextInt(3) - 1) + (rand.nextInt(3) - 1), -SPIKES_SPREAD_RADIUS, SPIKES_SPREAD_RADIUS);
            BlockPos base = findBestSpikeBase(w, cx + dx, cz + dz, yHint);
            if (base == null) continue;
            long key = (((long) base.getX()) << 32) ^ (base.getZ() & 0xffffffffL);
            if (!used.add(key)) continue;
            sc.spikes.add(new SpikeBase(base, pickSpikeHeight(rand)));
        }

        if (sc.spikes.isEmpty()) {
            sc.spikes.add(new SpikeBase(findSurfaceAirAboveSolid(w, cx, cz, yHint, true), SPIKES_MAX_HEIGHT));
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
                BlockPos candidate = findSurfaceAirAboveSolid(w, x + dx, z + dz, yHint, true);
                int d2 = dx * dx + dz * dz;
                if (d2 < bestD2) { bestD2 = d2; best = candidate; }
            }
        }
        return best;
    }

    /**
     * Walks downward from yHint+3 to find the first air block sitting above solid ground.
     * Handles caves, slabs, snow layers, and still-water surfaces correctly.
     */
    private static BlockPos findSurfaceAirAboveSolid(ServerLevel w, int x, int z, int yHint, boolean freezeWaterSurface) {
        int startY = Mth.clamp(yHint + 3, w.getMinBuildHeight() + 2, w.getHeight() - 2);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, startY, z);

        BlockState initial = w.getBlockState(m);
        boolean startedInSolid = !initial.getFluidState().isEmpty()
                || (!initial.isAir() && !initial.getCollisionShape(w, m).isEmpty());
        boolean foundAir = !startedInSolid;

        for (int i = 0; i < 80 && m.getY() > w.getMinBuildHeight() + 2; i++) {
            BlockState here  = w.getBlockState(m);
            BlockPos  below  = m.below();
            BlockState belowState = w.getBlockState(below);

            boolean hereOk = here.getFluidState().isEmpty()
                    && (here.isAir() || here.getCollisionShape(w, m).isEmpty());

            if (!foundAir) {
                if (hereOk) foundAir = true;
            } else if (!hereOk) {
                if (freezeWaterSurface && isStillWater(here)) freezeStillWaterToFrostedIce(w, m);
                return m.above().immutable();
            } else {
                boolean belowSolid = belowState.isFaceSturdy(w, below, Direction.UP);
                boolean belowWater = freezeWaterSurface && isStillWater(belowState);
                if (belowSolid || belowWater) {
                    if (belowWater) freezeStillWaterToFrostedIce(w, below);
                    return m.immutable();
                }
            }
            m.move(Direction.DOWN);
        }

        if (foundAir) {
            int top = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.min(top, startY);
            BlockPos fallback = new BlockPos(x, y, z);
            if (freezeWaterSurface && isStillWater(w.getBlockState(fallback.below())))
                freezeStillWaterToFrostedIce(w, fallback.below());
            return fallback;
        }
        return new BlockPos(x, yHint, z);
    }

    private static boolean isStillWater(BlockState s) {
        return s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource();
    }

    private static void freezeStillWaterToFrostedIce(ServerLevel w, BlockPos pos) {
        if (!isStillWater(w.getBlockState(pos))) return;
        w.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 2);
        w.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(w.random, 60, 120));
        w.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.22, 0.10, 0.22, 0.0);
    }

    private static void growAllSpikesToHeight(ServerLevel w, SpikeCast sc, int height) {
        Entity ownerEnt = w.getEntity(sc.owner);
        for (SpikeBase sb : sc.spikes) {
            int desired = Math.min(height, sb.maxHeight);
            if (desired <= 0) continue;
            setSpikeColumnHeight(w, sb.base, desired);
            if (desired != height) continue;

            BlockPos top = sb.base.above(desired - 1);
            if (ownerEnt != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(ownerEnt,
                        new IceSpikePuffPayload(top.getX() + 0.5, top.getY(), top.getZ() + 0.5));
            }
            hitEntitiesForSpikeStep(w, sc.owner, top);
        }
    }

    private static void setSpikeColumnHeight(ServerLevel w, BlockPos base, int height) {
        // Water check — abort if any position is waterlogged
        for (int i = 0; i < height; i++) {
            if (!w.getBlockState(base.above(i)).getFluidState().isEmpty()) return;
        }
        // Replaceability check — abort if a non-spike solid is in the way
        for (int i = 0; i < height; i++) {
            BlockPos p = base.above(i);
            BlockState s = w.getBlockState(p);
            boolean replaceable = s.isAir() || (s.getCollisionShape(w, p).isEmpty() && s.getFluidState().isEmpty());
            if (!replaceable && !s.is(ModBlocks.ICE_SPIKE.get())) return;
        }

        // height 1 → TIP; height 2 → FRUSTUM, TIP; height 3 → BASE, FRUSTUM, TIP
        DripstoneThickness[] layers = height == 1
                ? new DripstoneThickness[]{ DripstoneThickness.TIP }
                : height == 2
                ? new DripstoneThickness[]{ DripstoneThickness.FRUSTUM, DripstoneThickness.TIP }
                : new DripstoneThickness[]{ DripstoneThickness.BASE, DripstoneThickness.FRUSTUM, DripstoneThickness.TIP };

        for (int i = 0; i < height; i++) setSpikeState(w, base.above(i), layers[i]);
    }

    private static void setSpikeState(ServerLevel w, BlockPos pos, DripstoneThickness th) {
        w.setBlock(pos, ModBlocks.ICE_SPIKE.get().defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, th), 2);
    }

    private static void hitEntitiesForSpikeStep(ServerLevel w, UUID owner, BlockPos top) {
        Entity ownerEnt = w.getEntity(owner);
        if (!(ownerEnt instanceof ServerPlayer caster)) return;

        int nowTick = (int) (w.getGameTime() & 0x7fffffff);
        AABB box = new AABB(top).inflate(0.9, 1.6, 0.9);

        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != caster)) {
            IceVictimState state = getVictimState(e);
            if (state.lastSpikeHitTick == nowTick) continue;
            state.lastSpikeHitTick = nowTick;

            e.hurt(ModDamageTypes.iceSpike(w, caster), SPIKES_DAMAGE);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.min(SPIKES_MAX_Y_VEL, Math.max(v.y, SPIKES_KNOCKUP_Y)), v.z);
            e.hasImpulse = true;
            e.hurtMarked  = true; // explicit sync; don't rely solely on hurt() setting this
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            e.fallDistance = 0.0f;
            shatterOrFreeze(caster, e, SPIKES_FREEZE_STACKS);
        }
    }

    /* ============================================================
       SECONDARY — ICE BEAM
       ============================================================ */

    private static final long SECONDARY_COOLDOWN_MS      = 25_000;
    private static final int BEAM_CHARGE_TICKS           = 22;
    private static final int BEAM_FIRE_TICKS             = 125;
    private static final double BEAM_RANGE               = 34.0;
    private static final double BEAM_RADIUS              = 0.75;
    private static final int BEAM_APPLY_EVERY            = 2;
    private static final int BEAM_STACKS_PER_APPLY       = 5;
    private static final int BEAM_SLOW_AMP               = 1;
    private static final int BEAM_SLOW_TICKS             = 12;
    private static final float BEAM_DAMAGE               = 0.75f;
    private static final double BEAM_BLOCK_EPS           = 0.12;
    private static final int BEAM_SNOW_PLACE_EVERY       = 2;
    private static final int BEAM_WATER_FREEZE_EVERY     = 2;
    private static final int BEAM_MAX_WATER_FREEZES_PER_TICK = 6;

    private static final DustParticleOptions ULT_BLUE_DUST    = new DustParticleOptions(new Vector3f(0.25f, 0.65f, 1.00f), 0.75f);
    private static final DustParticleOptions ULT_SHIMMER_DUST = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 1.00f), 0.45f);

    @Override
    public void activateSecondary(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        if (state.beamChargeTicks > 0 || state.beamFireTicks > 0) return;
        state.beamChargeTicks = BEAM_CHARGE_TICKS;
        player.serverLevel().playSound(null, player.blockPosition(),
                ModSounds.ICEBEAMCHARGE.get(), player.getSoundSource(), 0.8f, 1.00f);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickBeam(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        ServerLevel w = player.serverLevel();

        if (state.beamChargeTicks > 0) {
            state.beamChargeTicks--;
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(0.0, v.y, 0.0);
            player.hasImpulse = true;
            player.hurtMarked  = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.setSprinting(false);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 4, true, false));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new IceBeamChargePayload(player.getId(), player.tickCount));
            if (state.beamChargeTicks <= 0) {
                state.beamFireTicks = BEAM_FIRE_TICKS;
                w.playSound(null, player.blockPosition(), ModSounds.ICEBEAMLOOP.get(), player.getSoundSource(), 0.9f, 1.55f);
            }
            return;
        }

        if (state.beamFireTicks > 0) {
            state.beamFireTicks--;
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(0.0, v.y, 0.0);
            player.hasImpulse = true;
            player.hurtMarked  = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.setSprinting(false);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, true, false));

            Vec3 dir    = player.getViewVector(1.0f).normalize();
            Vec3 muzzle = getBeamMuzzlePos(player, dir);
            Vec3 maxEnd = muzzle.add(dir.scale(BEAM_RANGE));

            BlockHitResult blockHit = w.clip(new ClipContext(muzzle, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 end = (blockHit.getType() == HitResult.Type.BLOCK)
                    ? blockHit.getLocation().subtract(dir.scale(BEAM_BLOCK_EPS))
                    : maxEnd;

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new IceBeamFirePayload(muzzle.x, muzzle.y, muzzle.z, end.x, end.y, end.z, w.getGameTime()));

            if (blockHit.getType() == HitResult.Type.BLOCK && (player.tickCount % BEAM_SNOW_PLACE_EVERY) == 0)
                tryLeaveSnowAtBeamImpact(w, blockHit);
            if ((player.tickCount % BEAM_WATER_FREEZE_EVERY) == 0) freezeWaterAlongBeam(w, muzzle, end);
            if ((player.tickCount % BEAM_APPLY_EVERY) == 0)        applyBeamEffects(w, player, muzzle, end);
            if ((player.tickCount % 15) == 0)
                w.playSound(null, player.blockPosition(), ModSounds.ICEBEAMLOOP.get(), player.getSoundSource(), 0.9f, 1.05f);

            if (state.beamFireTicks <= 0)
                w.playSound(null, player.blockPosition(), SoundEvents.GLASS_HIT, player.getSoundSource(), 0.65f, 1.8f);
        }
    }

    /** Applies freeze stacks, slow, drag, and damage to all entities hit by the beam in a single pass. */
    private static void applyBeamEffects(ServerLevel w, ServerPlayer caster, Vec3 start, Vec3 end) {
        AABB scan = new AABB(start, end).inflate(BEAM_RADIUS, BEAM_RADIUS, BEAM_RADIUS);
        double r2 = BEAM_RADIUS * BEAM_RADIUS;

        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, scan, e -> e.isAlive() && e != caster)) {
            if (distSqPointToSegment(e.position().add(0, e.getBbHeight() * 0.5, 0), start, end) > r2) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BEAM_SLOW_TICKS, BEAM_SLOW_AMP, true, false));
            applyBeamDrag(e);
            applyFreezePoints(caster, e, BEAM_STACKS_PER_APPLY);
            e.hurt(ModDamageTypes.iceBeam(w, caster), BEAM_DAMAGE);
        }
    }

    private static void tryLeaveSnowAtBeamImpact(ServerLevel w, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) return;
        BlockPos ground = hit.getBlockPos();
        BlockPos place  = ground.above();
        if (!w.getBlockState(place).isAir() || !w.getBlockState(ground).isFaceSturdy(w, ground, Direction.UP)) return;
        w.setBlock(place, Blocks.SNOW.defaultBlockState(), 2);
        w.sendParticles(ParticleTypes.SNOWFLAKE, place.getX() + 0.5, place.getY() + 0.05, place.getZ() + 0.5, 8, 0.25, 0.03, 0.25, 0.0);
    }

    private static void freezeWaterAlongBeam(ServerLevel w, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.25) return;
        Vec3 dir = delta.scale(1.0 / len);
        int samples = Mth.clamp((int) (len / 0.75), 3, 40);
        int froze = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= samples && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; i++) {
            Vec3 p = start.add(dir.scale(i * 0.75));
            m.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));

            for (int dx = -1; dx <= 1 && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; dx++) {
                for (int dy = -1; dy <= 1 && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; dy++) {
                    for (int dz = -1; dz <= 1 && froze < BEAM_MAX_WATER_FREEZES_PER_TICK; dz++) {
                        BlockPos pos = m.offset(dx, dy, dz);
                        BlockState s = w.getBlockState(pos);
                        if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) {
                            w.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 2);
                            w.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(w.random, 60, 120));
                            w.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, 6, 0.25, 0.15, 0.25, 0.0);
                            froze++;
                        }
                    }
                }
            }
        }
    }

    private static void applyBeamDrag(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        double ny = Math.max(v.y > 0.12 ? 0.12 : v.y - 0.08, -0.65);
        e.setDeltaMovement(v.x * 0.20, ny, v.z * 0.20);
        e.hasImpulse = true;
        e.hurtMarked  = true; // sync drag velocity to all tracking clients
        if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        e.fallDistance = 0.0f;
    }

    private static Vec3 getBeamMuzzlePos(ServerPlayer player, Vec3 dir) {
        Vec3 right = new Vec3(0, 1, 0).cross(dir);
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        double side = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) ? 0.24 : -0.24;
        return player.getEyePosition().add(dir.scale(0.30)).add(0.0, -0.28, 0.0).add(right.scale(side));
    }

    /* ============================================================
       ULTIMATE — BLIZZARD
       ============================================================ */

    private static final long ULT_COOLDOWN_MS             = 340_000;
    private static final int ULT_DURATION_TICKS           = 180;
    private static final double ULT_BLIZZARD_RADIUS       = 11.0;
    private static final int ULT_SNOW_PER_TICK            = 50;
    private static final int ULT_GUST_PER_TICK            = 30;
    private static final int ULT_FROST_RADIUS             = 4;
    private static final int ULT_FROST_EVERY              = 2;
    private static final int ULT_FROST_MAX_PER_TICK       = 10;
    private static final int ULT_SNOW_COVER_EVERY         = 5;
    private static final int ULT_SNOW_COVER_ATTEMPTS      = 45;
    private static final int ULT_SNOW_MAX_LAYERS          = 2;
    private static final int ULT_WAVE_INTERVAL            = 16;
    private static final double ULT_WAVE_SPEED            = 1.45;
    private static final double ULT_WAVE_MAX_RADIUS       = 14.0;
    private static final double ULT_WAVE_THICKNESS        = 0.80;
    private static final double ULT_WAVE_HEIGHT_OFFSET    = 0.10;
    private static final double ULT_WAVE_JUMP_CLEARANCE   = 0.55;
    private static final float ULT_WAVE_DAMAGE            = 5.5f;
    private static final double ULT_WAVE_KB               = 0.25;
    private static final double ULT_WAVE_UP               = 0.07;
    private static final int ULT_WAVE_FREEZE_STACKS       = 35;
    private static final int ULT_WAVE_SLOW_TICKS          = 20;
    private static final int ULT_WAVE_SLOW_AMP            = 1;
    private static final double ULT_SNOWMAN_CHANCE        = 0.002;

    private static final List<UltWave>                 ULT_WAVES      = new ArrayList<>();
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
        final double waveY, maxRadius;
        double radius;

        UltWave(int id, UUID owner, ResourceKey<Level> worldKey, Vec3 centerXZ, double waveY, double radius, double maxRadius) {
            this.id = id; this.owner = owner; this.worldKey = worldKey;
            this.centerXZ = centerXZ; this.waveY = waveY; this.radius = radius; this.maxRadius = maxRadius;
        }
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        state.ultActiveTicks = ULT_DURATION_TICKS;
        state.ultPulseTicks = 1;
        ServerLevel w = player.serverLevel();
        w.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, player.getSoundSource(), 0.9f, 0.75f);
        playBlizzardLoop(w, player);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickUltimate(ServerPlayer player) {
        IceCasterState state = getCasterState(player);
        if (state.ultActiveTicks <= 0) return;
        state.ultActiveTicks--;
        ServerLevel w = player.serverLevel();

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new IceBlizzardPayload(player.getId(), state.ultActiveTicks));

        if ((player.tickCount % ULT_SNOW_COVER_EVERY) == 0) spreadSnowCover(w, player);
        if (w.random.nextDouble() < ULT_SNOWMAN_CHANCE)     tryStartSnowmanBuild(w, player, state);
        tickActiveSnowmanBuilds(w, state);
        if ((player.tickCount % ULT_FROST_EVERY) == 0)      freezeWaterAroundCaster(w, player);

        if (state.ultPulseTicks > 0 && --state.ultPulseTicks <= 0) {
            state.ultPulseTicks = ULT_WAVE_INTERVAL;
            spawnUltWave(w, player);
            w.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 0.8f, 0.85f);
        }

        if (player.tickCount % 30 == 0) playBlizzardLoop(w, player);
        tickUltWavesWorld(w);
    }

    private static void spreadSnowCover(ServerLevel w, ServerPlayer caster) {
        Vec3 c = caster.position();
        for (int i = 0; i < ULT_SNOW_COVER_ATTEMPTS; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * Math.ceil(ULT_BLIZZARD_RADIUS);
            double a = w.random.nextDouble() * Math.PI * 2.0;
            int x = Mth.floor(c.x + Math.cos(a) * r);
            int z = Mth.floor(c.z + Math.sin(a) * r);

            BlockPos place = findSurfaceAirAboveSolid(w, x, z, caster.getBlockY(), true);
            if (w.isOutsideBuildHeight(place)) continue;

            BlockPos belowPos = place.below();
            BlockState below = w.getBlockState(belowPos);
            BlockState at    = w.getBlockState(place);

            if (!below.isFaceSturdy(w, belowPos, Direction.UP)) continue;

            if (at.is(Blocks.SNOW)) {
                int layers = at.getValue(SnowLayerBlock.LAYERS);
                if (layers < ULT_SNOW_MAX_LAYERS)
                    w.setBlock(place, at.setValue(SnowLayerBlock.LAYERS, layers + 1), 2);
                continue;
            }
            if (!at.isAir()) continue;

            w.setBlock(place, Blocks.SNOW.defaultBlockState(), 2);
            if ((i % 10) == 0)
                w.sendParticles(ParticleTypes.SNOWFLAKE, place.getX() + 0.5, place.getY() + 0.05, place.getZ() + 0.5, 6, 0.22, 0.03, 0.22, 0.0);
        }
    }

    private static void freezeWaterAroundCaster(ServerLevel w, ServerPlayer caster) {
        BlockPos center = caster.blockPosition();
        int froze = 0;
        for (int dx = -ULT_FROST_RADIUS; dx <= ULT_FROST_RADIUS && froze < ULT_FROST_MAX_PER_TICK; dx++) {
            for (int dz = -ULT_FROST_RADIUS; dz <= ULT_FROST_RADIUS && froze < ULT_FROST_MAX_PER_TICK; dz++) {
                if ((dx * dx + dz * dz) > ULT_FROST_RADIUS * ULT_FROST_RADIUS) continue;
                BlockPos pos = center.offset(dx, 0, dz);
                for (int dy = -1; dy <= 1 && froze < ULT_FROST_MAX_PER_TICK; dy++) {
                    BlockPos p = pos.offset(0, dy, 0);
                    BlockState s = w.getBlockState(p);
                    if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) {
                        w.setBlock(p, Blocks.FROSTED_ICE.defaultBlockState(), 2);
                        w.scheduleTick(p, Blocks.FROSTED_ICE, Mth.nextInt(w.random, 60, 120));
                        w.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 4, 0.20, 0.10, 0.20, 0.0);
                        if ((froze & 1) == 0)
                            w.sendParticles(ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.02, 0.02, 0.02, 0.0);
                        froze++;
                    }
                }
            }
        }
    }

    private static void spawnUltWave(ServerLevel w, ServerPlayer caster) {
        BlockPos groundAir = findSurfaceAirAboveSolid(w, caster.getBlockX(), caster.getBlockZ(), caster.getBlockY(), true);
        ULT_WAVES.add(new UltWave(
                nextUltWaveId(), caster.getUUID(), w.dimension(),
                new Vec3(caster.getX(), 0.0, caster.getZ()),
                groundAir.getY() + ULT_WAVE_HEIGHT_OFFSET,
                0.8, ULT_WAVE_MAX_RADIUS));
    }

    private static void tickUltWavesWorld(ServerLevel w) {
        if (!claimWorldTick(ULTW_LAST_TICK, w)) return;
        if (ULT_WAVES.isEmpty()) return;

        Iterator<UltWave> it = ULT_WAVES.iterator();
        while (it.hasNext()) {
            UltWave wave = it.next();
            if (!wave.worldKey.equals(w.dimension())) continue;
            wave.radius += ULT_WAVE_SPEED;
            spawnWaveRingParticles(w, wave);
            applyWaveHits(w, wave);
            if (wave.radius >= wave.maxRadius) it.remove();
        }
    }

    private static void spawnWaveRingParticles(ServerLevel w, UltWave wave) {
        double r      = wave.radius;
        double y      = wave.waveY;
        int    points = Mth.clamp((int) (r * 14.0), 28, 180);

        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * (Math.PI * 2.0);
            double x = wave.centerXZ.x + Math.cos(a) * r;
            double z = wave.centerXZ.z + Math.sin(a) * r;

            w.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.01, 0.02, 0.0);
            if ((i %  5) == 0) w.sendParticles(ULT_BLUE_DUST,        x, y + 0.02, z, 1, 0.02, 0.01, 0.02, 0.0);
            if ((i %  9) == 0) w.sendParticles(ULT_SHIMMER_DUST,     x, y + 0.06, z, 1, 0.02, 0.01, 0.02, 0.0);
            if ((i %  8) == 0) w.sendParticles(ParticleTypes.END_ROD, x, y + 0.05, z, 1, 0, 0, 0, 0.0);
            if ((i % 11) == 0) w.sendParticles(ParticleTypes.ENCHANT, x, y + 0.08, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static void applyWaveHits(ServerLevel w, UltWave wave) {
        Vec3   c     = wave.centerXZ;
        double scanR = wave.radius + ULT_WAVE_THICKNESS + 1.25;
        AABB   scan  = new AABB(c.x - scanR, wave.waveY - 3.0, c.z - scanR, c.x + scanR, wave.waveY + 4.0, c.z + scanR);

        List<LivingEntity> hits = w.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e.isAlive() && !e.getUUID().equals(wave.owner) && !(e instanceof SnowGolem));

        for (LivingEntity e : hits) {
            double eTop = e.getY() + e.getBbHeight();
            if (eTop < wave.waveY - 0.10 || e.getY() > wave.waveY + ULT_WAVE_JUMP_CLEARANCE) continue;

            Vec3 p = e.position();
            double dx   = p.x - c.x;
            double dz   = p.z - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - wave.radius) > ULT_WAVE_THICKNESS) continue;

            IceVictimState state = getVictimState(e);
            if (state.lastWaveHitId == wave.id) continue;
            state.lastWaveHitId = wave.id;

            Entity ownerEnt = w.getEntity(wave.owner);
            if (ownerEnt instanceof ServerPlayer caster) {
                e.hurt(ModDamageTypes.iceShockwave(w, caster), ULT_WAVE_DAMAGE);
                shatterOrFreeze(caster, e, ULT_WAVE_FREEZE_STACKS);
            }

            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ULT_WAVE_SLOW_TICKS, ULT_WAVE_SLOW_AMP, true, false));
            double inv   = (dist < 1.0e-4) ? 0.0 : (1.0 / dist);
            Vec3   v     = e.getDeltaMovement();
            double newY  = Math.min(0.55, Math.max(v.y, ULT_WAVE_UP));
            e.setDeltaMovement(v.x + dx * inv * ULT_WAVE_KB, newY, v.z + dz * inv * ULT_WAVE_KB);
            e.hasImpulse = true;
            e.hurtMarked  = true; // explicit sync; hurt() is conditional on caster type so can't be relied on
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            e.fallDistance = 0.0f;
        }
    }

    private static void playBlizzardLoop(ServerLevel world, ServerPlayer caster) {
        double r2 = ULT_BLIZZARD_RADIUS * ULT_BLIZZARD_RADIUS;
        for (ServerPlayer p : world.players()) {
            if (p.distanceToSqr(caster) <= r2) {
                world.playSound(null, p.blockPosition(), ModSounds.BLIZZARDLOOP.get(), p.getSoundSource(), 0.3f, 0.8f);
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
        if (w.getBlockState(pos).isAir()) state.snowmen.add(new SnowmanBuild(pos));
    }

    private static void tickActiveSnowmanBuilds(ServerLevel w, IceCasterState state) {
        Iterator<SnowmanBuild> it = state.snowmen.iterator();
        while (it.hasNext()) {
            SnowmanBuild build = it.next();
            if (--build.delay > 0) continue;

            if (build.step == 1) {
                w.setBlock(build.pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                w.playSound(null, build.pos, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1f, 1f);
                build.step = 2; build.delay = 15;
            } else if (build.step == 2) {
                w.setBlock(build.pos.above(), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                w.playSound(null, build.pos.above(), SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
                build.step = 3; build.delay = 15;
            } else {
                w.setBlock(build.pos.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState(), 3);
                w.sendParticles(ParticleTypes.SNOWFLAKE, build.pos.getX() + 0.5, build.pos.getY() + 2, build.pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.05);
                it.remove();
            }
        }
    }

    /* ============================================================
       GEOMETRY HELPERS
       ============================================================ */

    private static double distSqPointToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double abLen2 = ab.lengthSqr();
        if (abLen2 < 1.0e-9) return p.distanceToSqr(a);
        double t = Mth.clamp((p.subtract(a)).dot(ab) / abLen2, 0.0, 1.0);
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    public static boolean cleanseFreeze(LivingEntity target) {
        IceVictimState state = VICTIM_STATES.get(target.getUUID());
        if (state != null && state.freezePoints > 0) { clearFreeze(target); return true; }
        return false;
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName()            { return Component.translatable("power.loopypowers.ice.name").getString(); }
    @Override public String getPrimaryName()     { return Component.translatable("power.loopypowers.ice.primary_name").getString(); }
    @Override public String getSecondaryName()   { return Component.translatable("power.loopypowers.ice.secondary_name").getString(); }
    @Override public String getUltimateName()    { return Component.translatable("power.loopypowers.ice.ultimate_name").getString(); }
    @Override public String getPassiveName()     { return Component.translatable("power.loopypowers.ice.passive_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return PRIMARY_COOLDOWN_MS; }
    @Override public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }
    @Override public long getUltimateCooldownMs()  { return ULT_COOLDOWN_MS; }

    @Override public String getOverviewDescription()  { return Component.translatable("power.loopypowers.ice.description.overview").getString(); }
    @Override public String getPassiveDescription()   { return Component.translatable("power.loopypowers.ice.description.passive").getString(); }
    @Override public String getPrimaryDescription()   { return Component.translatable("power.loopypowers.ice.description.primary").getString(); }
    @Override public String getSecondaryDescription() { return Component.translatable("power.loopypowers.ice.description.secondary").getString(); }
    @Override public String getUltimateDescription()  { return Component.translatable("power.loopypowers.ice.description.ultimate").getString(); }
}