package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.power.SoundPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SonicBoltEntity extends Entity {

    /* ============================================================
       TAGS / CONSTANTS
       ============================================================ */

    private ServerPlayer owner;
    private int life;
    private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();

    // ── Entity tuning ────────────────────────────────────────────────────────

    // how long before it dies
    private static final int    MAX_LIFE              = 60;
    // how far ahead to spawn particles
    private static final double PARTICLE_FORWARD_DIST = 11.0;
    private static final int    VIBRATION_TICKS       = 14;
    // hit detection size
    private static final double SWEEP_RADIUS          = 1.5;
    private static final double HITBOX_INFLATION      = 0.4;
    // impact numbers
    private static final float  BASE_DAMAGE           = 5.0f;
    private static final double KNOCKBACK             = 1.1;
    private static final double KNOCKUP               = 0.25;

    public SonicBoltEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public void setOwner(ServerPlayer owner) {
        this.owner = owner;
    }

    public ServerPlayer getOwner() {
        return owner;
    }

    /* ============================================================
       TICK LOGIC
       ============================================================ */

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        life++;
        if (life > MAX_LIFE || owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }

        Vec3 current = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 next = current.add(vel);

        handleCollisions(current, next);

        this.setPos(next.x, next.y, next.z);

        if (this.level() instanceof ServerLevel sw) {
            Vec3 dir = vel.normalize();
            Vec3 target = current.add(dir.scale(PARTICLE_FORWARD_DIST));

            if (this.tickCount % 2 == 0) {
                sw.sendParticles(new VibrationParticleOption(new BlockPositionSource(BlockPos.containing(target)), VIBRATION_TICKS),
                        this.getX(), this.getY() + 0.05, this.getZ(), 1, 0, 0, 0, 0);
            }

            sw.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                    this.getX(), this.getY() + 0.05, this.getZ(), 4, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void handleCollisions(Vec3 prev, Vec3 next) {
        AABB sweep = new AABB(prev, next).inflate(SWEEP_RADIUS);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e.isAlive() && e != owner);

        for (LivingEntity t : targets) {
            if (hit.contains(t.getUUID())) continue;

            var opt = t.getBoundingBox().inflate(HITBOX_INFLATION).clip(prev, next);
            if (opt.isEmpty()) continue;

            hit.add(t.getUUID());

            // do the hit/stun first so the velocity reset doesn't cancel our bolt knockback
            SoundPower.applyAbilityHit(owner, t, BASE_DAMAGE, true);

            Vec3 dir = next.subtract(prev).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
            dir = dir.normalize();

            t.setDeltaMovement(t.getDeltaMovement().add(dir.x * KNOCKBACK, KNOCKUP, dir.z * KNOCKBACK));
            t.hasImpulse = true;
        }
    }

    /* ============================================================
       DATA
       ============================================================ */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.life = nbt.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putInt("Life", this.life);
    }
}