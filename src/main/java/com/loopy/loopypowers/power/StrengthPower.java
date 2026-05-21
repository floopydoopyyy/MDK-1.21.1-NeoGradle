package com.loopy.loopypowers.power;

import com.loopy.loopypowers.manager.AbilityTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.tags.BlockTags;
import org.joml.Vector3f;
import com.loopy.loopypowers.damage.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.network.PacketDistributor;
import com.loopy.loopypowers.network.payload.StrengthParticlePayload;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StrengthPower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, StrengthState> ACTIVE_STATES = new HashMap<>();

    private static class StrengthState {
        int rushTicks = 0;
        Vec3 rushDir = null;
        int rushHitLock = 0;
        int rushCancelLock = 0;

        int rageTicks = 0;
        int rageFxBurst = 0;
        int rageAuraStep = 0;
        int rageHbStep = 0;
    }

    private static StrengthState getState(ServerPlayer player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new StrengthState());
    }

    /* ============================================================
       EGG
       ============================================================ */
    public static boolean onePunchDebugEnabled = false;
    private static final float ONE_PUNCH_CHANCE = 0.02f;

    /* ============================================================
       TUNING
       ============================================================ */

    // Bullrush
    private static final int RUSH_TICKS = 28;     // duration
    private static final double RUSH_SPEED = 1.25;
    private static final double RUSH_HIT_RADIUS = 1.3;
    private static final int RUSH_CANCEL_COOLDOWN_TICKS = 4;
    private static final float RUSH_HIT_DAMAGE = 13.5f;
    private static final float RUSH_HIT_KNOCKUP = 0.95f;
    private static final double RUSH_WALL_CHECK_DIST = 0.75;
    private static final int RUSH_WALL_MAX_BLOCKS = 18;
    private static final float RUSH_WALL_MAX_HARDNESS = 3.5f;
    private static final float RUSH_WALL_BREAK_CHANCE = 0.80f;
    private static final float RUSH_CRASH_SELF_DAMAGE = 4.0f;
    private static final float RUSH_CRASH_AOE_DAMAGE = 6.0f;
    private static final double RUSH_CRASH_AOE_RADIUS = 4.5;
    private static final double RUSH_MAX_TURN_DEG = 4.5; // Degrees per tick of steering

    // Rage
    private static final int RAGE_TICKS = 240; // 12s
    private static final int RAGE_STR_AMP = 1;    // Strength II
    private static final int RAGE_RES_AMP = 0;    // Resistance I
    private static final int RAGE_SPEED_AMP = 0;  // Speed I

    // Slam
    private static final int SLAM_BLOCK_RADIUS = 3;
    private static final int SLAM_MAX_BLOCKS_BROKEN = 22;
    private static final float SLAM_MAX_HARDNESS = 2.2f;
    private static final float SLAM_BLOCK_BREAK_CHANCE = 0.55f;
    private static final float SLAM_ENTITY_DAMAGE = 14.0f;
    private static final float SLAM_OUT = 0.35f;
    private static final float SLAM_FRONT_DOT = 0.35f;
    private static final double SLAM_FRONT_OFFSET = 1.4;
    private static final double SLAM_RADIUS = 5.5;
    private static final float SLAM_UP = 1.55f;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("st_")); // Clean legacy tags
        ACTIVE_STATES.put(player.getUUID(), new StrengthState());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("st_"));
        ACTIVE_STATES.remove(player.getUUID());

        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        StrengthState state = getState(player);

        if (!PassiveManager.isEnabled(player)) return;

        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        if (strength == null || strength.getDuration() < 5) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0, true, false));
        }

        // Tick timers
        if (state.rushHitLock > 0) state.rushHitLock--;
        if (state.rushCancelLock > 0) state.rushCancelLock--;

        if (state.rushTicks > 0) {
            state.rushTicks--;
            tickBullrush(player, state);
        }

        if (state.rageTicks > 0) {
            state.rageTicks--;
            tickRageBuffs(player);
            tickRageFX(player, state);
        }
    }

    @Override
    public boolean onAttack(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
        if (!PassiveManager.isEnabled(attacker)) return true;

        // GUARD CLAUSE
        if (amount >= 9999f) return true;

        // ONE PUNCH EASTER EGG
        if (source.getEntity() == attacker && attacker.getMainHandItem().isEmpty()) {
            boolean isUnarmoredPlayer = (target instanceof ServerPlayer) && (target.getArmorValue() == 0);
            if (onePunchDebugEnabled || (isUnarmoredPlayer && attacker.level().random.nextFloat() < ONE_PUNCH_CHANCE)) {
                ServerLevel w = attacker.serverLevel();

                w.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), ModSounds.ONEPUNCH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

                Vec3 dir = attacker.getViewVector(1.0f).normalize();
                target.setDeltaMovement(dir.x * 25.0, 4.0, dir.z * 25.0);
                target.hasImpulse = true;
                target.hurtMarked = true; // sync velocity to all tracking clients, not just ServerPlayers

                if (target instanceof ServerPlayer spTarget) {
                    spTarget.connection.send(new ClientboundSetEntityMotionPacket(spTarget));
                }

                Vec3 pos = target.position();

                // CLIENT DISPATCH: One Punch Particles
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new StrengthParticlePayload(
                        StrengthParticlePayload.ONE_PUNCH, pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, 0, false
                ));

                target.hurt(ModDamageTypes.onePunch(w, attacker), 9999f);
                return false; // Cancel original punch damage
            }
        }
        return true;
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        StrengthState state = getState(attacker);
        if (state.rageTicks <= 0) return;

        ServerLevel w = attacker.serverLevel();

        Vec3 d = target.position().subtract(attacker.position());
        Vec3 horiz = new Vec3(d.x, 0.0, d.z);
        if (horiz.lengthSqr() < 1.0e-6) horiz = new Vec3(0, 0, 1);
        Vec3 dir = horiz.normalize();

        double out = 0.95;
        double lift = target.onGround() ? 0.18 : 0.08;
        double maxUp = 0.55;

        target.setLastHurtByMob(attacker);

        target.setDeltaMovement(target.getDeltaMovement().add(dir.x * out, lift, dir.z * out));
        Vec3 tv = target.getDeltaMovement();
        target.setDeltaMovement(tv.x, Math.min(maxUp, Math.max(tv.y, 0.06)), tv.z);
        target.hasImpulse = true;
        target.hurtMarked = true; // sync velocity to all tracking clients, not just ServerPlayers

        if (target instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }

        // CLIENT DISPATCH: Rage Target Hit Particles
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new StrengthParticlePayload(
                StrengthParticlePayload.RAGE_HIT, target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(), 0, 0, 0, 0, false
        ));

        w.playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                attacker.getSoundSource(),
                0.8f,
                0.85f
        );
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        Vec3 pos = player.position();

        boolean casterGrounded = isNearGround(player, w);

        Vec3 forward = player.getViewVector(1.0f);
        Vec3 slamPos = pos.add(forward.x, 0.0, forward.z);

        if (!casterGrounded) {
            applySlamDrag(player);
        }

        BlockHitResult wallHit = findWallSlamHit(w, player, forward);
        boolean isWallSlam = (wallHit != null);

        if (!casterGrounded && !isWallSlam) {
            doGroundSlam(player, w, pos, forward, slamPos, false);
            return;
        }

        if (isWallSlam) {
            doWallSlam(player, w, pos, forward, slamPos, wallHit);
        } else {
            doGroundSlam(player, w, pos, forward, slamPos, true);
        }
    }

    private static void doGroundSlam(ServerPlayer player, ServerLevel w, Vec3 pos, Vec3 forward, Vec3 slamPos, boolean casterGrounded) {
        BlockPos ground = player.blockPosition().below();
        BlockState groundState = w.getBlockState(ground);

        BlockPos slamGround = BlockPos.containing(slamPos).below();
        BlockState slamGroundState = w.getBlockState(slamGround);
        if (!slamGroundState.isAir()) {
            ground = slamGround;
            groundState = slamGroundState;
        }

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.SLAM.get(),
                player.getSoundSource(),
                casterGrounded ? 1.00f : 0.35f,
                casterGrounded ? 0.85f : 1.10f
        );

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(),
                casterGrounded ? 1.00f : 0.35f,
                casterGrounded ? 0.85f : 1.10f
        );

        // CLIENT DISPATCH: Ground Slam Burst
        int stateId = Block.getId(groundState);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new StrengthParticlePayload(
                StrengthParticlePayload.GROUND_SLAM, slamPos.x, pos.y + 0.10, slamPos.z, 0, 0, 0, stateId, casterGrounded
        ));

        if (casterGrounded) {
            CameraShake.shakeNearby(player, 6.0, 10, 1.05f);
        } else {
            CameraShake.shakeNearby(player, 4.0, 6, 0.35f);
        }

        AABB box = new AABB(slamPos, slamPos).inflate(SLAM_RADIUS, 2.5, SLAM_RADIUS);

        List<LivingEntity> targets = w.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity t : targets) {
            Vec3 d = t.position().subtract(slamPos);
            Vec3 horiz = new Vec3(d.x, 0.0, d.z);
            if (horiz.lengthSqr() < 1.0e-6) horiz = new Vec3(0, 0, 1);
            Vec3 dir = horiz.normalize();

            double dist = Math.sqrt(horiz.lengthSqr());
            float falloff = 1.0f - (float) Mth.clamp(dist / SLAM_RADIUS, 0.0, 1.0);

            double upRaw  = SLAM_UP  * (0.85 + 0.95 * falloff);
            double outRaw = SLAM_OUT * (0.35 + 1.0  * falloff);

            double kbMult = casterGrounded ? 1.0 : 0.15;
            double dmgMult = casterGrounded ? 1.0 : 0.15;

            double maxUp = 1.15;
            double minUpGround = 0.65;
            double maxOut = 0.55;

            double up = Mth.clamp(upRaw * kbMult, 0.0, maxUp);
            double out = Mth.clamp(outRaw * kbMult, 0.0, maxOut);

            if (t.onGround()) {
                up = Math.max(up, minUpGround * kbMult);
            }

            t.setLastHurtByMob(player);

            float dmg = (float) (SLAM_ENTITY_DAMAGE * (0.6f + 0.6f * falloff) * dmgMult);
            if (dmg > 0.0f) {
                t.hurt(ModDamageTypes.slam(w, player), dmg);
            }

            Vec3 v = t.getDeltaMovement();
            t.setDeltaMovement(v.x + dir.x * out, Math.max(v.y, up), v.z + dir.z * out);
            t.hasImpulse = true;
            t.hurtMarked = true; // sync velocity to all tracking clients, not just ServerPlayers

            if (t instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }

            if (casterGrounded && w.random.nextFloat() < 0.35f) {
                // CLIENT DISPATCH: Slam Target Crit
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(t, new StrengthParticlePayload(
                        StrengthParticlePayload.SLAM_TARGET_CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.55, t.getZ(), 0, 0, 0, 0, false
                ));
            }
        }

        if (casterGrounded) {
            int broken = 0;

            Vec3 slamCentreVec = pos.add(forward.x * SLAM_FRONT_OFFSET, 0.0, forward.z * SLAM_FRONT_OFFSET);
            BlockPos center = BlockPos.containing(slamCentreVec).below();

            int r = SLAM_BLOCK_RADIUS;

            for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
                if (broken >= SLAM_MAX_BLOCKS_BROKEN) break;

                BlockState s = w.getBlockState(p);
                if (s.isAir()) continue;
                if (w.getBlockEntity(p) != null) continue;

                float hardness = s.getDestroySpeed(w, p);
                if (hardness < 0) continue;
                if (hardness > SLAM_MAX_HARDNESS) continue;

                double dx = p.getX() + 0.5 - pos.x;
                double dz = p.getZ() + 0.5 - pos.z;

                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 1.0e-6) {
                    double nx = dx / len;
                    double nz = dz / len;

                    Vec3 f = new Vec3(forward.x, 0.0, forward.z);
                    if (f.lengthSqr() > 1.0e-6) f = f.normalize();

                    double dot = nx * f.x + nz * f.z;
                    if (dot < SLAM_FRONT_DOT) continue;
                }

                double d2 = dx * dx + dz * dz;
                if (d2 > (r + 0.25) * (r + 0.25)) continue;

                if (w.random.nextFloat() > SLAM_BLOCK_BREAK_CHANCE) continue;

                if (w.destroyBlock(p, true, player)) {
                    broken++;
                }
            }
        }

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void doWallSlam(ServerPlayer player, ServerLevel w, Vec3 pos, Vec3 forward, Vec3 slamPos, BlockHitResult wallHit) {
        boolean casterGrounded = true;

        doGroundSlam(player, w, pos, forward, slamPos, casterGrounded);

        int wallMaxBreak = 14;
        float wallMaxHardness = 3.5f;
        int broken = 0;

        BlockPos hitPos = wallHit.getBlockPos();
        Vec3 n = Vec3.atLowerCornerOf(wallHit.getDirection().getNormal());

        int halfW = 1;
        int halfH = 1;
        int depth = 2;

        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= wallMaxBreak) break;

                    BlockPos p;
                    if (xWall) {
                        p = hitPos.offset((int)(-n.x * d), y, xz);
                    } else {
                        p = hitPos.offset(xz, y, (int)(-n.z * d));
                    }

                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    float hardness = s.getDestroySpeed(w, p);
                    if (hardness < 0) continue;
                    if (hardness > wallMaxHardness) continue;

                    if (w.random.nextFloat() > 0.75f) continue;

                    if (w.destroyBlock(p, true, player)) {
                        broken++;
                    }
                }
            }
        }
    }

    private static BlockHitResult findWallSlamHit(ServerLevel w, ServerPlayer player, Vec3 forward) {
        double maxDist = 2.2;

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(forward.normalize().scale(maxDist));

        BlockHitResult bhr = w.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (bhr.getType() != HitResult.Type.BLOCK) return null;

        BlockState s = w.getBlockState(bhr.getBlockPos());
        if (s.isAir()) return null;

        return bhr;
    }

    private static void applySlamDrag(ServerPlayer player) {
        Vec3 v = player.getDeltaMovement();

        double newY;
        if (v.y > 0.0) {
            newY = -0.12;
        } else {
            newY = Math.min(v.y, -0.12);
        }

        player.setDeltaMovement(v.x, newY, v.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static boolean isNearGround(ServerPlayer player, ServerLevel w) {
        final double leeway = 1.60;

        if (player.onGround()) return true;

        Vec3 start = player.position().add(0.0, 0.05, 0.0);
        Vec3 end   = start.add(0.0, -leeway, 0.0);

        BlockHitResult hr = w.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hr.getType() != HitResult.Type.BLOCK) return false;

        BlockState s = w.getBlockState(hr.getBlockPos());
        return !s.isAir();
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayer player) {
        StrengthState state = getState(player);
        state.rushTicks = RUSH_TICKS;
        state.rushDir = player.getViewVector(1.0f).normalize();
        state.rushHitLock = 0;
        state.rushCancelLock = RUSH_CANCEL_COOLDOWN_TICKS;

        ServerLevel w = player.serverLevel();

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.LUNGESTART.get(),
                player.getSoundSource(),
                1.2f, 1.0f);

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.BULLRUSH.get(),
                player.getSoundSource(),
                1.5f, 1.0f);

        enableRushStepUp(player, true);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickBullrush(ServerPlayer player, StrengthState state) {
        if (state.rushTicks <= 0) {
            enableRushStepUp(player, false);
            return;
        }

        // CANCEL
        if (state.rushCancelLock <= 0 && player.isCrouching()) {
            state.rushTicks = 0;
            enableRushStepUp(player, false);

            ServerLevel w = player.serverLevel();
            w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BULLRUSH.get(), player.getSoundSource(), 0.6f, 0.9f);

            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new StrengthParticlePayload(
                    StrengthParticlePayload.RUSH_CANCEL, player.getX(), player.getY() + 0.2, player.getZ(), 0, 0, 0, 0, false
            ));
            return;
        }

        Vec3 dir = state.rushDir;
        if (dir == null) dir = player.getViewVector(1.0f).normalize();

        double maxTurn = Math.toRadians(RUSH_MAX_TURN_DEG);

        Vec3 look = player.getViewVector(1.0f);
        Vec3 a = new Vec3(dir.x, 0.0, dir.z);
        Vec3 b = new Vec3(look.x, 0.0, look.z);

        if (a.lengthSqr() > 1e-5 && b.lengthSqr() > 1e-5) {
            a = a.normalize();
            b = b.normalize();
            double dot = Mth.clamp(a.dot(b), -1.0, 1.0);
            double ang = Math.acos(dot);
            if (ang > maxTurn) {
                Vec3 cross = a.cross(b);
                double sign = cross.y >= 0 ? -1.0 : 1.0;
                double clampedAng = sign * maxTurn;
                double cos = Math.cos(clampedAng);
                double sin = Math.sin(clampedAng);
                double nx = a.x * cos - a.z * sin;
                double nz = a.x * sin + a.z * cos;
                dir = new Vec3(nx, look.y, nz).normalize();
            } else {
                dir = look;
            }
            state.rushDir = dir;
        }

        ServerLevel w = player.serverLevel();

        Vec3 horiz = new Vec3(dir.x, 0.0, dir.z);
        if (horiz.lengthSqr() < 1.0e-6) horiz = new Vec3(1, 0, 0);

        BlockPos basePos = player.blockPosition();
        for (BlockPos bPos : BlockPos.betweenClosed(basePos.offset(-1, 0, -1), basePos.offset(1, 1, 1))) {
            BlockState bs = w.getBlockState(bPos);
            if (!bs.isAir()) {
                if (bs.canBeReplaced() || bs.is(BlockTags.LEAVES) || bs.is(BlockTags.FLOWERS)
                        || bs.is(BlockTags.SMALL_FLOWERS) || bs.is(BlockTags.TALL_FLOWERS)) {
                    w.destroyBlock(bPos, true, player);
                }
            }
        }

        BlockHitResult wallHit = findRushWallHit(w, player, dir);
        if (wallHit != null) {
            state.rushTicks = 0;
            enableRushStepUp(player, false);
            doRushCrash(player, w, wallHit);
            return;
        }

        if (!tryRushStepUp(player, w, horiz)) {
            player.setDeltaMovement(horiz.x * RUSH_SPEED, player.getDeltaMovement().y, horiz.z * RUSH_SPEED);
            player.hasImpulse = true;
        }

        // SYNC
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // FX
        Vec3 pCenter = player.position().add(0, 0.1, 0);
        boolean playStep = w.getGameTime() % 2 == 0;
        int stateId = Block.getId(w.getBlockState(player.blockPosition().below()));

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new StrengthParticlePayload(
                StrengthParticlePayload.RUSH_TICK, pCenter.x, pCenter.y, pCenter.z, horiz.x, horiz.y, horiz.z, stateId, playStep
        ));

        if (playStep) {
            w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_STEP, player.getSoundSource(), 0.5f, 0.8f);
        }

        // ENTITY COLLISION
        boolean canHit = state.rushHitLock <= 0;
        if (canHit) {
            Vec3 p = player.position().add(horiz.normalize().scale(0.9));
            AABB hitBox = new AABB(p, p).inflate(RUSH_HIT_RADIUS, 1.2, RUSH_HIT_RADIUS);

            List<LivingEntity> hits = w.getEntitiesOfClass(
                    LivingEntity.class,
                    hitBox,
                    e -> e.isAlive() && e != player
            );

            if (!hits.isEmpty()) {
                for (LivingEntity t : hits) {
                    t.setLastHurtByMob(player);

                    t.hurt(ModDamageTypes.rushCollision(w, player), RUSH_HIT_DAMAGE);

                    Vec3 tv = t.getDeltaMovement();
                    t.setDeltaMovement(tv.x, Math.max(tv.y, RUSH_HIT_KNOCKUP), tv.z);
                    t.hasImpulse = true;
                    t.hurtMarked = true; // sync velocity to all tracking clients, not just ServerPlayers

                    if (t instanceof ServerPlayer sp) {
                        sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                    }

                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(t, new StrengthParticlePayload(
                            StrengthParticlePayload.RUSH_HIT, t.getX(), t.getY() + t.getBbHeight() * 0.55, t.getZ(), 0, 0, 0, 0, false
                    ));
                }

                w.playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.ENTITYSLAM.get(),
                        player.getSoundSource(), 0.9f, 0.9f);

                state.rushHitLock = 6;
            }
        }
    }

    private static BlockHitResult findRushWallHit(ServerLevel w, ServerPlayer player, Vec3 dir) {
        Vec3 horiz = new Vec3(dir.x, 0.0, dir.z);
        if (horiz.lengthSqr() < 1.0e-6) return null;

        Vec3 fwd = horiz.normalize().scale(RUSH_WALL_CHECK_DIST);

        Vec3 lowStart = player.position().add(0.0, 0.20, 0.0);
        Vec3 lowEnd   = lowStart.add(fwd);

        HitResult low = w.clip(new ClipContext(
                lowStart, lowEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 highStart = player.position().add(0.0, player.getBbHeight() * 0.90, 0.0);
        Vec3 highEnd   = highStart.add(fwd);

        BlockHitResult high = w.clip(new ClipContext(
                highStart, highEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        boolean lowBlock  = (low.getType()  == HitResult.Type.BLOCK);
        boolean highBlock = (high.getType() == HitResult.Type.BLOCK);

        if (lowBlock && !highBlock) return null;

        if (!highBlock) return null;
        return high;
    }

    private static void doRushCrash(ServerPlayer player, ServerLevel w, BlockHitResult wallHit) {
        BlockPos hitPos = wallHit.getBlockPos();

        player.setDeltaMovement(0, player.getDeltaMovement().y * 0.25, 0);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        Vec3 impact = wallHit.getLocation();
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.WALLSLAM.get(),
                player.getSoundSource(), 1.0f, 1.00f);

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                player.getSoundSource(), 0.8f, 0.85f);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new StrengthParticlePayload(
                StrengthParticlePayload.RUSH_CRASH, impact.x, impact.y, impact.z, 0, 0, 0, 0, false
        ));

        int broken = 0;

        Vec3 n = Vec3.atLowerCornerOf(wallHit.getDirection().getNormal());

        int halfW = 1;
        int halfH = 1;
        int depth = 2;

        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= RUSH_WALL_MAX_BLOCKS) break;

                    BlockPos p;
                    if (xWall) {
                        p = hitPos.offset((int) (-n.x * d), y, xz);
                    } else {
                        p = hitPos.offset(xz, y, (int) (-n.z * d));
                    }

                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    float hardness = s.getDestroySpeed(w, p);
                    if (hardness < 0) continue;
                    if (hardness > RUSH_WALL_MAX_HARDNESS) continue;

                    if (w.random.nextFloat() > RUSH_WALL_BREAK_CHANCE) continue;

                    if (w.destroyBlock(p, true, player)) broken++;
                }
            }
        }

        player.hurt(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_SELF_DAMAGE);

        AABB box = new AABB(impact, impact).inflate(RUSH_CRASH_AOE_RADIUS, 2.0, RUSH_CRASH_AOE_RADIUS);

        List<LivingEntity> victims = w.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity t : victims) {
            t.setLastHurtByMob(player);
            t.hurt(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_AOE_DAMAGE);

            Vec3 tv = t.getDeltaMovement();
            t.setDeltaMovement(tv.x, Math.max(tv.y, 0.65), tv.z);
            t.hasImpulse = true;
            t.hurtMarked = true; // sync velocity to all tracking clients, not just ServerPlayers

            if (t instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }
        }

        CameraShake.shakeNearby(player, 8.0, 10, 1.15f);
    }

    private static void enableRushStepUp(ServerPlayer player, boolean enable) {
        var attr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null) {
            attr.setBaseValue(enable ? 1.05 : 0.6);
        }
    }

    private static boolean tryRushStepUp(ServerPlayer player, ServerLevel w, Vec3 horizDir) {
        if (!player.onGround() && player.getDeltaMovement().y > 0.12) return false;

        Vec3 fwd = horizDir.normalize();
        if (fwd.lengthSqr() < 1.0e-6) return false;

        Vec3 feet = player.position().add(0.0, 0.05, 0.0);
        Vec3 ahead = feet.add(fwd.scale(0.8));

        BlockPos front = BlockPos.containing(ahead);
        BlockPos frontUp = front.above();

        BlockState sFront = w.getBlockState(front);
        var shape = sFront.getCollisionShape(w, front);
        if (shape.isEmpty()) return false;

        if (shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.56) return false;

        BlockState sFrontUp = w.getBlockState(frontUp);
        if (!sFrontUp.getCollisionShape(w, frontUp).isEmpty()) return false;

        if (player.getDeltaMovement().y > 0.30) return false;

        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.max(v.y, 0.52), v.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        return true;
    }

    /* ============================================================
       ULT
       ============================================================ */

    private static final int RAGE_FX_RADIUS = 10;
    private static final int RAGE_AURA_INTERVAL = 2;
    private static final int RAGE_HB_INTERVAL = 14;

    @Override
    public void activateUltimate(ServerPlayer player) {
        StrengthState state = getState(player);
        state.rageTicks = RAGE_TICKS;
        state.rageFxBurst = 2;
        state.rageAuraStep = 0;
        state.rageHbStep = 0;

        ServerLevel w = player.serverLevel();

        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.RAGE.get(),
                player.getSoundSource(), 0.8f, 1.00f);

        PowerManager.clearAbilityCooldown(player, AbilityTypes.PRIMARY);
        PowerManager.clearAbilityCooldown(player, AbilityTypes.SECONDARY);
    }

    private static void tickRageBuffs(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 10, RAGE_STR_AMP, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, RAGE_RES_AMP, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, RAGE_SPEED_AMP, true, false));
    }

    public static boolean isRaging(ServerPlayer player) {
        return ACTIVE_STATES.getOrDefault(player.getUUID(), new StrengthState()).rageTicks > 0;
    }

    private static final DustParticleOptions RAGE_RED_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.35f);

    private static void tickRageFX(ServerPlayer player, StrengthState state) {
        ServerLevel w = player.serverLevel();

        if (state.rageFxBurst >= 0) {
            spawnRagePulse(w, player);
            CameraShake.shakeNearby(player, RAGE_FX_RADIUS, 18, 1.85f);
            state.rageFxBurst = -1;
        }

        if (state.rageAuraStep <= 0) {
            state.rageAuraStep = RAGE_AURA_INTERVAL;

            // FX - Client Dispatch (Enhanced Scary Aura handled on client)
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new StrengthParticlePayload(
                    StrengthParticlePayload.RAGE_TICK, player.getX(), player.getY(), player.getZ(), 0, 0, 0, 0, false
            ));
        }
        else {
            state.rageAuraStep--;
        }

        if (state.rageHbStep <= 0) {
            state.rageHbStep = RAGE_HB_INTERVAL;

            Vec3 c = player.position();
            AABB box = new AABB(c, c).inflate(RAGE_FX_RADIUS, 6.0, RAGE_FX_RADIUS);
            List<ServerPlayer> nearbyPlayers = w.getEntitiesOfClass(
                    ServerPlayer.class,
                    box,
                    ServerPlayer::isAlive
            );

            for (ServerPlayer p : nearbyPlayers) {
                p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.95f, 0.95f);
            }
        } else {
            state.rageHbStep--;
        }
    }

    private static void spawnRagePulse(ServerLevel w, ServerPlayer player) {
        double cx = player.getX();
        double cy = player.getY() + 1.0;
        double cz = player.getZ();

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new StrengthParticlePayload(
                StrengthParticlePayload.RAGE_PULSE, cx, cy, cz, 0, 0, 0, 0, false
        ));
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return Component.translatable("power.loopypowers.strength.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.strength.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.strength.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.strength.ultimate_name").getString(); }

    @Override
    public long getPrimaryCooldownMs() { return 12_500; }

    @Override
    public long getSecondaryCooldownMs() {
        return 18_500;
    }

    @Override
    public long getUltimateCooldownMs() {
        return 280_000;
    }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.strength.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.strength.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.strength.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.strength.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.strength.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.strength.description.ultimate").getString();
    }
}