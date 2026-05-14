package com.loopy.loopypowers.power;

import com.loopy.loopypowers.block.ModBlocks;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import com.loopy.loopypowers.damage.ModDamageTypes;

import java.util.*;

/**
 * Nature power skeleton (1.20.1 / Fabric -> 1.21.1)
 * Focus: area denial + trapping + "hunt" marking.
 */
public class NaturePower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, NatureState> ACTIVE_STATES = new HashMap<>();

    private static class NatureState {
        final List<GasInstance> gases = new ArrayList<>();
        CageState cage = null;
        final Map<UUID, VineBind> vines = new HashMap<>();
    }

    private static NatureState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new NatureState());
    }

    /* ============================================================
       EASTER EGGS
       ============================================================ */

    private static final float FLOWER_CORPSE_CHANCE = 0.15f;
    private static final float PVZ_KILL_CHANCE = 0.03f;
    private static final Block[] FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY};

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        NatureState state = getState(player);
        removeCageNow(player, state);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        NatureState state = ACTIVE_STATES.remove(player.getUUID());

        // Cleanup personal buffs
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DIG_SPEED);

        if (state == null) return;

        // Cleanup cage
        removeCageNow(player, state);

        // Cleanup vines
        if (player.getServer() != null) {
            for (VineBind b : state.vines.values()) {
                ServerLevel w = player.getServer().getLevel(b.worldKey);
                if (w != null) {
                    Entity ent = w.getEntity(b.target);
                    if (ent instanceof LivingEntity le) {
                        le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        le.removeEffect(MobEffects.GLOWING);
                        le.removeEffect(ModEffects.TETHERED);
                    }
                }
            }
        }
        state.vines.clear();
        state.gases.clear();
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public boolean onAttack(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
        if (amount >= target.getHealth()) {
            // PVZ Zombie Pop
            if (target instanceof net.minecraft.world.entity.monster.Zombie) {
                if (source.is(ModDamageTypes.THORN) || source.is(ModDamageTypes.VINE_BIND)) {
                    if (target.level().random.nextFloat() < PVZ_KILL_CHANCE) {
                        target.level().playSound(null, target.blockPosition(), ModSounds.PVZPOP.get(), SoundSource.HOSTILE, 0.7f, 1.0f);
                    }
                }
            }

            // Flower Planting
            if (target.level() instanceof ServerLevel sw) {
                BlockPos under = target.blockPosition().below();
                if (sw.getBlockState(under).is(Blocks.GRASS_BLOCK) && sw.getBlockState(target.blockPosition()).isAir()) {
                    if (sw.random.nextFloat() < FLOWER_CORPSE_CHANCE) {
                        Block flower = FLOWERS[sw.random.nextInt(FLOWERS.length)];
                        sw.setBlock(target.blockPosition(), flower.defaultBlockState(), 3);
                        sw.playSound(null, target.blockPosition(), SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.6f, 1.0f);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        NatureState state = getState(player);

        // PASSIVE
        if (PassiveManager.isEnabled(player)) {
            tickPhotosynthesis(player);
        }

        // PRIMARY
        tickExpandingGas(player, state);

        // SECONDARY
        tickCage(player, state);

        // ULT
        tickHunt(player, state);
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final int PASSIVE_REFRESH_TICKS = 20; // every time it is refreshed
    private static final int PASSIVE_REGEN_AMP = 0;      // regen provided
    private static final int PASSIVE_REGEN_TICKS = 40;   // how long given for

    private static void tickPhotosynthesis(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        // refresh rhythm
        if (player.tickCount % PASSIVE_REFRESH_TICKS != 0) return;

        boolean inWater = player.isInWater();
        boolean inSun = w.isDay() && w.canSeeSky(player.blockPosition()) && !w.isRaining();

        if (inWater || inSun) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    PASSIVE_REGEN_TICKS,
                    PASSIVE_REGEN_AMP,
                    true,
                    false
            ));
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */
    // duration
    private static final int GAS_DURATION_TICKS = 280;

    // range
    private static final float GAS_RADIUS_START = 1.8f;
    private static final float GAS_RADIUS_MAX   = 6.6f;

    // vertical volume
    private static final float GAS_HEIGHT_START = 1.8f;
    private static final float GAS_HEIGHT_MAX   = 3.6f;

    // Poison
    private static final int GAS_POISON_TICKS = 90;         // time
    private static final int GAS_POISON_AMP   = 2;          // amp
    private static final int GAS_APPLY_INTERVAL_TICKS = 10;  // checks
    private static final int GAS_REAPPLY_THRESHOLD = 35;    // only refresh when low

    // fog
    private static final int GAS_PARTICLES_MIN = 30;
    private static final int GAS_PARTICLES_MAX = 110;

    // stinky
    private static final float STINK_CHANCE = 0.02f;

    // make green dust
    private static final DustParticleOptions GAS_DUST =
            new DustParticleOptions(new Vector3f(0.12f, 0.95f, 0.18f), 1.75f);

    private static final class GasInstance {
        final ResourceKey<Level> worldKey;
        final Vec3 center;
        final int seed;
        int age;

        GasInstance(ResourceKey<Level> worldKey, Vec3 center, int seed) {
            this.worldKey = worldKey;
            this.center = center;
            this.seed = seed;
            this.age = 0;
        }
    }

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        NatureState state = getState(player);

        // Spawn in front of player
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 spawn = player.position().add(look.scale(1.2)).add(0, 0.6, 0);

        // Create a deterministic gas instance
        int seed = (int)(w.getGameTime() ^ player.getUUID().getLeastSignificantBits());
        state.gases.add(new GasInstance(w.dimension(), spawn, seed));

        // The stink easter egg
        net.minecraft.sounds.SoundEvent sound = w.random.nextFloat() < STINK_CHANCE ? ModSounds.STINK.get() : ModSounds.SPRAY.get();

        w.playSound(null, player.blockPosition(),
                sound,
                player.getSoundSource(),
                0.8f, 0.9f);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickExpandingGas(ServerPlayer player, NatureState state) {
        if (state.gases.isEmpty()) return;

        long now = player.serverLevel().getGameTime();

        Iterator<GasInstance> it = state.gases.iterator();
        while (it.hasNext()) {
            GasInstance g = it.next();
            ServerLevel w = Objects.requireNonNull(player.getServer()).getLevel(g.worldKey);
            if (w == null) continue;

            g.age++;
            if (g.age >= GAS_DURATION_TICKS) {
                it.remove();
                continue;
            }

            // progress 0..1
            float t = g.age / (float) GAS_DURATION_TICKS;
            t = Mth.clamp(t, 0.0f, 1.0f);

            float r = Mth.lerp(t, GAS_RADIUS_START, GAS_RADIUS_MAX);
            float h = Mth.lerp(t, GAS_HEIGHT_START, GAS_HEIGHT_MAX);

            Vec3 center = g.center;

            // centered vertical volume
            double halfH = h * 0.5;
            double minY = center.y - halfH;
            double maxY = center.y + halfH;

            // VISUALS
            int count = Mth.floor(Mth.lerp(t, GAS_PARTICLES_MIN, GAS_PARTICLES_MAX));

            // thick core
            w.sendParticles(
                    GAS_DUST,
                    center.x, center.y, center.z,
                    count,
                    r * 0.90, halfH * 0.90, r * 0.90,
                    0.02
            );

            // boundary wisps (readable edge)
            if (((now + g.seed) & 1L) == 0L) {
                w.sendParticles(
                        GAS_DUST,
                        center.x, center.y, center.z,
                        Math.max(20, count / 3),
                        r * 1.10, halfH * 0.55, r * 1.10,
                        0.03
                );
            }

            // occasional particles
            if (((now + g.seed) % 10L) == 0L) {
                w.sendParticles(
                        GAS_DUST,
                        center.x, center.y, center.z,
                        120,
                        r * 0.70, halfH * 0.70, r * 0.70,
                        0.04
                );
            }

            // POISON
            if (((g.age + g.seed) % GAS_APPLY_INTERVAL_TICKS) != 0) continue;

            // Small vertical padding so it feels like “fog volume” not a razor slab
            double yPad = 0.35;

            AABB scan = new AABB(
                    center.x - r, (minY - yPad), center.z - r,
                    center.x + r, (maxY + yPad), center.z + r
            );

            List<LivingEntity> hits = w.getEntitiesOfClass(
                    LivingEntity.class,
                    scan,
                    e -> e.isAlive() && !e.getUUID().equals(player.getUUID())
            );

            double r2 = r * r;

            for (LivingEntity e : hits) {
                var bb = e.getBoundingBox();

                // must overlap vertically with the gas volume
                if (bb.maxY < (minY - yPad) || bb.minY > (maxY + yPad)) continue;

                // XZ cylinder check using closest point on entity AABB
                double closestX = Mth.clamp(center.x, bb.minX, bb.maxX);
                double closestZ = Mth.clamp(center.z, bb.minZ, bb.maxZ);

                double dx = closestX - center.x;
                double dz = closestZ - center.z;

                if ((dx * dx + dz * dz) > r2) continue;

                // apply poison
                MobEffectInstance cur = e.getEffect(MobEffects.POISON);
                if (cur == null || cur.getDuration() <= GAS_REAPPLY_THRESHOLD) {
                    e.addEffect(new MobEffectInstance(
                            MobEffects.POISON,
                            GAS_POISON_TICKS,
                            GAS_POISON_AMP,
                            true,
                            false
                    ));
                }
            }
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final int CAGE_LIFETIME_TICKS = 240; // time up

    private static final int CAGE_RADIUS = 12;
    private static final int CAGE_POINTS = 68; // ring density
    private static final int CAGE_HEIGHT = 6;
    private static final int CAGE_THICKNESS = 2;

    private static final class CageState {
        int ticksLeft = CAGE_LIFETIME_TICKS;
        ResourceKey<Level> worldKey;
        final List<BlockPos> placed = new ArrayList<>();

        CageState(ResourceKey<Level> worldKey) {
            this.worldKey = worldKey;
        }
    }

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        NatureState state = getState(player);

        w.playSound(null, player.blockPosition(), ModSounds.ARENABUILD.get(), SoundSource.BLOCKS, 0.8f, 1.0f);

        removeCageNow(player, state);

        state.cage = new CageState(w.dimension());

        int cx = player.blockPosition().getX();
        int cz = player.blockPosition().getZ();
        int aroundY = player.blockPosition().getY();

        // build a ring on the ground
        for (int i = 0; i < CAGE_POINTS; i++) {
            double a = (Math.PI * 2.0) * (i / (double) CAGE_POINTS);

            for (int t = 0; t < CAGE_THICKNESS; t++) {
                int rNow = Math.max(1, CAGE_RADIUS - t);

                int x = cx + (int) Math.round(Math.cos(a) * rNow);
                int z = cz + (int) Math.round(Math.sin(a) * rNow);

                // find ground
                int baseAirY = findLocalFloorAirY(w, x, z, aroundY);

                // Build column
                for (int y = 0; y < CAGE_HEIGHT; y++) {
                    BlockPos pos = new BlockPos(x, baseAirY + y, z);

                    // only replace air
                    BlockState existing = w.getBlockState(pos);
                    if (!existing.getFluidState().isEmpty()) continue;
                    if (!existing.getCollisionShape(w, pos).isEmpty() && !existing.isAir()) continue;

                    BlockState vine = ModBlocks.THORN_VINE.get().defaultBlockState();
                    w.setBlock(pos, vine, 3);
                    state.cage.placed.add(pos);
                }
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static int findLocalFloorAirY(ServerLevel w, int x, int z, int aroundY) {
        int startY = Mth.clamp(aroundY + 1, w.getMinBuildHeight() + 2, w.getHeight() - 2);

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, startY, z);

        // search downward for air above
        for (int i = 0; i < 18 && m.getY() > w.getMinBuildHeight() + 2; i++) {
            BlockPos belowPos = m.below();

            BlockState here = w.getBlockState(m);
            BlockState below = w.getBlockState(belowPos);

            boolean hereEmpty = here.getCollisionShape(w, m).isEmpty() && here.getFluidState().isEmpty();
            boolean belowSolid = !below.getCollisionShape(w, belowPos).isEmpty() && below.getFluidState().isEmpty();

            if (hereEmpty && belowSolid) {
                return m.getY(); // air block directly above ground
            }

            m.move(Direction.DOWN);
        }

        // fallback - find surface
        return w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void tickCage(ServerPlayer player, NatureState state) {
        if (state.cage == null) return;

        state.cage.ticksLeft--;
        if (state.cage.ticksLeft > 0) return;

        removeCageNow(player, state);
    }

    private static void removeCageNow(ServerPlayer player, NatureState state) {
        if (state.cage == null) return;

        ServerLevel w = Objects.requireNonNull(player.getServer()).getLevel(state.cage.worldKey);
        if (w != null) {
            for (BlockPos pos : state.cage.placed) {
                if (w.getBlockState(pos).is(ModBlocks.THORN_VINE.get())) {
                    w.destroyBlock(pos, false);
                }
            }
        }
        state.cage = null;
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */
    // tuning
    private static final int VINE_DURATION_TICKS = 220; // time stuck
    private static final double VINE_RANGE = 22.0;
    private static final int VINE_MAX_TARGETS = 8;
    private static final double VINE_CONE_DOT = 0.80; // cone degrees
    // tether behavior
    private static final double VINE_TETHER_RADIUS = 5.5;     // how far they can roam from the anchor
    private static final double VINE_PULL_MIN = 0.06;         // smallest pull when just outside
    private static final double VINE_PULL_MAX = 0.45;         // strongest pull when far outside
    private static final double VINE_OVER_PULL_SCALE = 0.18;  // pull strength scale vs "over distance"
    // debuff + tick damage
    private static final int VINE_SLOW_REFRESH = 10;
    private static final int VINE_SLOW_TICKS = 20;
    private static final int VINE_SLOW_AMP = 1;
    private static final int VINE_DAMAGE_INTERVAL = 20;
    private static final float VINE_DAMAGE = 1.0f;
    // caster buffs near a vined enemy
    private static final double VINE_BUFF_RANGE = 12.0;
    private static final int VINE_BUFF_REFRESH = 10;
    private static final int VINE_BUFF_TICKS = 30;
    //lash visuals
    private static final int VINE_STRIKE_LASHES_PER_TARGET = 3; // extra vine lines per target
    private static final int VINE_STRIKE_EXTRA_RANDOM = 6;      // extra “miss” lashes forward

    // visuals
    private static final DustParticleOptions VINE_DUST =
            new DustParticleOptions(new Vector3f(0.10f, 0.85f, 0.12f), 1.35f);

    // pink
    private static final DustParticleOptions VINE_PINK_DUST =
            new DustParticleOptions(new Vector3f(0.95f, 0.35f, 0.85f), 1.05f);

    private static final float VINE_PINK_SPECK_CHANCE = 0.12f; // ~12% of particles become pink

    private static final class VineBind {
        final UUID target;                // bound entity
        final ResourceKey<Level> worldKey;
        final Vec3 anchor;               // where they were hit by ult
        final int seed;
        int age;

        VineBind(UUID target, ResourceKey<Level> worldKey, Vec3 anchor, int seed) {
            this.target = target;
            this.worldKey = worldKey;
            this.anchor = anchor;
            this.seed = seed;
            this.age = 0;
        }
    }
    // debug
    private static final String DEBUG_VINE_SELF = "nature_debug_vine_self";

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        NatureState state = getState(player);

        // cast FX
        w.sendParticles(VINE_DUST,
                player.getX(), player.getY() + 1.0, player.getZ(),
                80,
                0.75, 0.95, 0.75,
                0.02
        );
        w.playSound(null, player.blockPosition(),
                ModSounds.VINELASH.get(),
                player.getSoundSource(),
                1.0f, 0.8f);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();

        // spawn vines from a hand
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = up.cross(look);
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();

        Vec3 lashFrom = eye
                .add(look.scale(0.35))     // a bit forward
                .add(right.scale(0.35))    // to the side
                .add(0.0, -0.35, 0.0);        // slightly down

        AABB box = new AABB(player.position(), player.position()).inflate(VINE_RANGE, 10, VINE_RANGE);

        List<LivingEntity> candidates = w.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        int taken = 0;
        int seedBase = (int)(w.getGameTime() ^ player.getUUID().getLeastSignificantBits());

        // DEBUG to try tether self
        if (player.getTags().contains(DEBUG_VINE_SELF)) {
            player.getTags().remove(DEBUG_VINE_SELF);

            int seed = seedBase ^ 0xBEEF;
            Vec3 anchor = player.position();

            state.vines.put(player.getUUID(), new VineBind(player.getUUID(), w.dimension(), anchor, seed));

            // anchor particles
            w.sendParticles(VINE_DUST, anchor.x, anchor.y + 0.2, anchor.z, 35, 0.35, 0.15, 0.35, 0.02);

            player.swing(InteractionHand.MAIN_HAND, true);
            return;
        }

        for (LivingEntity e : candidates) {
            if (taken >= VINE_MAX_TARGETS) break;

            // cone range check
            Vec3 targetPoint = e.position().add(0, e.getBbHeight() * 0.65, 0);
            Vec3 toTarget = targetPoint.subtract(eye);

            double distSq = toTarget.lengthSqr();
            if (distSq < 1.0e-6) continue;
            if (distSq > (VINE_RANGE * VINE_RANGE)) continue;

            Vec3 dir = toTarget.normalize();
            double dot = look.dot(dir);
            if (dot < VINE_CONE_DOT) continue;

            // LOS check
            if (!hasLineOfSight(w, player, e, eye)) continue;

            // anchor point is where they were when hit
            Vec3 anchor = e.position();

            // store bind
            int seed = seedBase ^ e.getId();

            // main strike line
            spawnVineStrike(w, lashFrom, e.position().add(0, e.getBbHeight() * 0.65, 0), seed);

            // extra lash lines around the target
            spawnVineLashes(w, lashFrom, e, seed);

            state.vines.put(e.getUUID(), new VineBind(e.getUUID(), w.dimension(), anchor, seed));

            // bind FX on target
            w.sendParticles(VINE_DUST,
                    e.getX(), e.getY() + (e.getBbHeight() * 0.55), e.getZ(),
                    40,
                    0.45, 0.45, 0.45,
                    0.02
            );
            // pink
            w.sendParticles(VINE_PINK_DUST,
                    e.getX(), e.getY() + (e.getBbHeight() * 0.55), e.getZ(),
                    6,
                    0.35, 0.35, 0.35,
                    0.01
            );

            w.sendParticles(VINE_DUST,
                    anchor.x, anchor.y + 0.2, anchor.z,
                    25,
                    0.35, 0.15, 0.35,
                    0.02
            );

            taken++;
        }

        // extra lashes
        Random rr = new Random(seedBase);
        for (int i = 0; i < VINE_STRIKE_EXTRA_RANDOM; i++) {
            double d = 6.0 + rr.nextDouble() * (VINE_RANGE - 6.0);
            Vec3 to = lashFrom.add(look.scale(d));

            // spread around crosshair
            double ox = (rr.nextDouble() - 0.5) * 3.5;
            double oy = (rr.nextDouble() - 0.5) * 2.0;
            double oz = (rr.nextDouble() - 0.5) * 3.5;

            spawnVineStrike(w, lashFrom, to.add(ox, oy, oz), seedBase ^ (i * 991));
        }

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickHunt(ServerPlayer player, NatureState state) {
        tickVines(player, state);

        // buffs for the caster near vined
        if (player.tickCount % VINE_BUFF_REFRESH != 0) return;

        ServerLevel w = player.serverLevel();
        ResourceKey<Level> wk = w.dimension();

        boolean nearAny = false;
        double r2 = VINE_BUFF_RANGE * VINE_BUFF_RANGE;

        for (VineBind b : state.vines.values()) {
            if (!b.worldKey.equals(wk)) continue;

            Entity ent = w.getEntity(b.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;

            if (le.distanceToSqr(player) <= r2) {
                nearAny = true;
                break;
            }
        }

        if (nearAny) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, VINE_BUFF_TICKS, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, VINE_BUFF_TICKS, 0, true, false));

            // visuals while buffed
            spawnBuffRing(w, player);
        }
    }

    private static void tickVines(ServerPlayer player, NatureState state) {
        if (state.vines.isEmpty()) return;
        long now = player.serverLevel().getGameTime();

        Iterator<VineBind> it = state.vines.values().iterator();
        while (it.hasNext()) {
            VineBind b = it.next();
            ServerLevel w = Objects.requireNonNull(player.getServer()).getLevel(b.worldKey);
            if (w == null) continue;

            b.age++;
            if (b.age >= VINE_DURATION_TICKS) {
                it.remove();
                continue;
            }

            Entity ent = w.getEntity(b.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }

            // === tether particles ===
            if (((now + b.seed) % 3L) == 0L) {
                Vec3 a = b.anchor.add(0, 0.2, 0);
                Vec3 t = le.position().add(0, le.getBbHeight() * 0.55, 0);
                spawnVineTether(w, a, t, b.seed);
            }

            // effects
            if (((b.age + b.seed) % VINE_SLOW_REFRESH) == 0) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, VINE_SLOW_TICKS, VINE_SLOW_AMP, true, false));
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 15, 0, true, false));
                le.addEffect(new MobEffectInstance(ModEffects.TETHERED, 5, 0, true, false));
            }

            //soft tether
            double dx = b.anchor.x - le.getX();
            double dz = b.anchor.z - le.getZ();
            double distSq = dx * dx + dz * dz;

            double rad = VINE_TETHER_RADIUS;
            double radSq = rad * rad;

            if (distSq > radSq) {
                double dist = Math.sqrt(distSq);
                double over = dist - rad;

                // pull strength scales with how far outside they are
                double pull = Mth.clamp(over * VINE_OVER_PULL_SCALE, VINE_PULL_MIN, VINE_PULL_MAX);

                double nx = dx / dist;
                double nz = dz / dist;

                le.setDeltaMovement(le.getDeltaMovement().add(nx * pull, 0.0, nz * pull));
                le.hasImpulse = true;

                // for players, force sync so it feels consistent
                if (le instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                    sp.setSprinting(false);
                }
            }

            // tick damage
            if (((b.age + b.seed) % VINE_DAMAGE_INTERVAL) == 0) {
                // don't attribute caster to stop annoying knockback
                le.hurt(ModDamageTypes.vineBind(w), VINE_DAMAGE);
            }
        }
    }

    private static boolean hasLineOfSight(ServerLevel w, ServerPlayer caster, LivingEntity target, Vec3 casterEye) {
        Vec3 targetPoint = target.position().add(0, target.getBbHeight() * 0.85, 0);

        HitResult hr = w.clip(new ClipContext(
                casterEye,
                targetPoint,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster
        ));

        if (hr.getType() == HitResult.Type.MISS) return true;

        // if the hit is basically at the target point, treat it as LOS
        double hitSq = hr.getLocation().distanceToSqr(casterEye);
        double endSq = targetPoint.distanceToSqr(casterEye);
        return hitSq >= (endSq - 0.05);
    }

    private static void spawnVineTether(ServerLevel w, Vec3 from, Vec3 to, int seed) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = Mth.clamp((int)(len * 10), 10, 60);
        Vec3 step = delta.scale(1.0 / steps);

        Vec3 p = from;
        for (int i = 0; i <= steps; i++) {
            DustParticleOptions eff = ((w.getGameTime() + seed + i) % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;

            w.sendParticles(
                    eff,
                    p.x, p.y, p.z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0
            );
            p = p.add(step);
        }

        // “anchor” puff
        if ((w.getGameTime() & 3L) == 0L) {
            w.sendParticles(
                    VINE_DUST,
                    from.x, from.y + 0.15, from.z,
                    8,
                    0.20, 0.10, 0.20,
                    0.01
            );
            w.sendParticles(
                    VINE_PINK_DUST,
                    from.x, from.y + 0.15, from.z,
                    2,
                    0.20, 0.10, 0.20,
                    0.01
            );
        }
    }

    private static void spawnVineStrike(ServerLevel w, Vec3 from, Vec3 to, int seed) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = Mth.clamp((int)(len * 14), 12, 90);
        Vec3 step = delta.scale(1.0 / steps);

        Random r = new Random(seed);
        Vec3 p = from;

        for (int i = 0; i <= steps; i++) {
            // jitter
            double j = 0.05 + (r.nextDouble() * 0.04);
            double jx = (r.nextDouble() - 0.5) * j;
            double jy = (r.nextDouble() - 0.5) * j;
            double jz = (r.nextDouble() - 0.5) * j;

            DustParticleOptions eff = (r.nextFloat() < VINE_PINK_SPECK_CHANCE) ? VINE_PINK_DUST : VINE_DUST;

            w.sendParticles(
                    eff,
                    p.x + jx, p.y + jy, p.z + jz,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );

            p = p.add(step);
        }

        // hit stuff
        w.sendParticles(
                VINE_DUST,
                to.x, to.y, to.z,
                8,
                0.20, 0.20, 0.20,
                0.02
        );
        w.sendParticles(
                VINE_PINK_DUST,
                to.x, to.y, to.z,
                2,
                0.20, 0.20, 0.20,
                0.02
        );
    }

    private static void spawnVineLashes(ServerLevel w, Vec3 lashFrom, LivingEntity target, int seed) {
        Random r = new Random(seed ^ 0x52A1B);

        Vec3 base = target.position().add(0, target.getBbHeight() * 0.65, 0);

        for (int i = 0; i < VINE_STRIKE_LASHES_PER_TARGET; i++) {
            double ox = (r.nextDouble() - 0.5) * 1.8;
            double oy = (r.nextDouble() - 0.5) * 1.2;
            double oz = (r.nextDouble() - 0.5) * 1.8;

            spawnVineStrike(w, lashFrom, base.add(ox, oy, oz), seed ^ (i * 1337));
        }
    }

    private static void spawnBuffRing(ServerLevel w, ServerPlayer player) {
        Vec3 c = player.position();
        double y = c.y + 0.15;

        int points = 24;
        double radius1 = 0.85;
        double radius2 = 1.15;

        // rotate over time
        double spin = (w.getGameTime() * 0.22);

        for (int i = 0; i < points; i++) {
            double a = spin + (Math.PI * 2.0) * (i / (double) points);

            double x1 = c.x + Math.cos(a) * radius1;
            double z1 = c.z + Math.sin(a) * radius1;

            double x2 = c.x + Math.cos(a + 0.35) * radius2;
            double z2 = c.z + Math.sin(a + 0.35) * radius2;

            DustParticleOptions eff1 = (w.getGameTime() % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;
            w.sendParticles(eff1, x1, y, z1, 1, 0.0, 0.0, 0.0, 0.0);

            if ((i & 1) == 0) {
                DustParticleOptions eff2 = ((w.getGameTime() + i) % 11L == 0L) ? VINE_PINK_DUST : VINE_DUST;
                w.sendParticles(eff2, x2, y + 0.10, z2, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        w.sendParticles(
                VINE_DUST,
                c.x, c.y + 0.9, c.z,
                6,
                0.20, 0.35, 0.20,
                0.01
        );
        w.sendParticles(
                VINE_PINK_DUST,
                c.x, c.y + 0.9, c.z,
                2,
                0.20, 0.35, 0.20,
                0.01
        );
    }

    // Called by HealingPower's purge ability to cleanse the player
    public static boolean cleanseVines(ServerPlayer player) {
        boolean removed = false;
        for (NatureState state : ACTIVE_STATES.values()) {
            if (state.vines.remove(player.getUUID()) != null) {
                removed = true;
            }
        }
        return removed;
    }

    @Override public String getName() { return Component.translatable("power.loopypowers.nature.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.nature.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.nature.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.nature.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs() { return 18_000; }
    @Override public long getSecondaryCooldownMs() { return 38_000; }
    @Override public long getUltimateCooldownMs() { return 350_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.nature.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.nature.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.nature.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.nature.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.nature.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.nature.description.ultimate").getString();
    }
}