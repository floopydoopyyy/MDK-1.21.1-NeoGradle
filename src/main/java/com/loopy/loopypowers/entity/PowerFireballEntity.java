package com.loopy.loopypowers.entity;

// import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.CameraShake;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

// FireballEntity maps to LargeFireball in Mojmap
public class PowerFireballEntity extends LargeFireball {
    private float directHitDamage = 6.0f;
    private float extraExplosionDamage = 0.0f;
    private Vec3 lastTrailPos = null;
    private int myExplosionPower = 1;
    private Vec3 constantVelocity = Vec3.ZERO;
    private Vec3 lastServerPos = null;
    private int stuckTicks = 0;

    public PowerFireballEntity(EntityType<? extends LargeFireball> type, Level level) {
        super(type, level);
    }

    public PowerFireballEntity(
            EntityType<? extends LargeFireball> type,
            Level level,
            LivingEntity owner,
            double vx, double vy, double vz,
            int explosionPower
    ) {
        // AbstractHurtingProjectile constructor in 1.21.1 uses Vec3 for movement usually, or super(type, owner, level)
        super(type, level);

        this.setOwner(owner);
        this.myExplosionPower = explosionPower;
        this.setPos(owner.getX(), owner.getY() + 1.5D, owner.getZ());

        // no acceleration
        this.setDeltaMovement(vx, vy, vz);
        this.constantVelocity = new Vec3(vx, vy, vz);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            this.setDeltaMovement(constantVelocity);

            Vec3 p = this.position();
            if (lastServerPos != null && p.distanceToSqr(lastServerPos) < 1.0e-8) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastServerPos = p;

            if (stuckTicks >= 2) {
                // createMissed becomes miss
                this.onHit(BlockHitResult.miss(
                        this.position(),
                        this.getDirection(),
                        this.blockPosition()
                ));
                return;
            }

            FluidState fs = this.level().getFluidState(this.blockPosition());
            if (!fs.isEmpty()) {
                explodeInWater();
                return;
            }

            return;
        }

        var world = this.level();
        Vec3 now = this.position();

        if (lastTrailPos == null) {
            lastTrailPos = now;
            return;
        }

        Vec3 delta = now.subtract(lastTrailPos);
        int steps = 6;

        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;

            double px = lastTrailPos.x + delta.x * t;
            double py = lastTrailPos.y + delta.y * t;
            double pz = lastTrailPos.z + delta.z * t;

            double ox = (this.random.nextDouble() - 0.5) * 0.08;
            double oy = (this.random.nextDouble() - 0.5) * 0.08;
            double oz = (this.random.nextDouble() - 0.5) * 0.08;

            world.addParticle(ParticleTypes.FLAME, px + ox, py + oy, pz + oz, 0, 0, 0);
            world.addParticle(ParticleTypes.SMOKE, px + ox, py + oy, pz + oz, 0, 0, 0);
            world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px + ox, py + oy, pz + oz, 0, 0, 0);
        }

        lastTrailPos = now;
    }

    // getDrag becomes getInertia
    @Override
    protected float getInertia() {
        return 1.0f;
    }

    public void setDamageValues(float directHitDamage, float extraExplosionDamage) {
        this.directHitDamage = directHitDamage;
        this.extraExplosionDamage = extraExplosionDamage;
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);

        Entity hit = entityHitResult.getEntity();
        Entity owner = this.getOwner();

        DamageSource src = this.damageSources().fireball(this, owner);
        hit.hurt(src, this.directHitDamage);

        if (!this.level().isClientSide) {
            float strength = 0.8f;
            double radius = 12.0;
            int ticks = 8;

            if (hit instanceof ServerPlayer hitPlayer) {
                CameraShake.shakeNearby(hitPlayer, radius, ticks, strength);
            } else if (owner instanceof ServerPlayer ownerPlayer) {
                CameraShake.shakeNearby(ownerPlayer, radius, ticks, strength);
            }
        }

        if (hit instanceof LivingEntity living) {
            // setOnFireFor becomes igniteForSeconds
            living.igniteForSeconds(4);
        }
    }

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        if (this.level().isClientSide) {
            super.onHit(hitResult);
            return;
        }

        if (this.level() instanceof ServerLevel serverWorld) {
            Entity owner = this.getOwner();

            // createExplosion becomes explode
            serverWorld.explode(
                    this,
                    this.damageSources().explosion(this, owner),
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    (float) myExplosionPower,
                    true,
                    Level.ExplosionInteraction.MOB // ExplosionSourceType becomes ExplosionInteraction
            );
        }

        if (extraExplosionDamage > 0.0f && this.level() instanceof ServerLevel serverWorld) {
            float radius = Math.max(2.0f, extraExplosionDamage * 0.35f);
            AABB box = new AABB(this.position(), this.position()).inflate(radius);

            Entity owner = this.getOwner();
            DamageSource src = this.damageSources().explosion(this, owner);

            // getOtherEntities becomes getEntities
            for (Entity e : serverWorld.getEntities(this, box)) {
                if (!(e instanceof LivingEntity living)) continue;
                if (owner != null && e == owner) continue;

                double dist = e.distanceToSqr(this); // squaredDistanceTo becomes distanceToSqr
                double max = radius * radius;
                float scale = (float) Math.max(0.0, 1.0 - (dist / max));

                living.hurt(src, extraExplosionDamage * scale);
            }
        }

        this.discard();
    }

    private void explodeInWater() {
        if (!(this.level() instanceof ServerLevel serverWorld)) return;

        Entity owner = this.getOwner();

        serverWorld.explode(
                this,
                this.damageSources().explosion(this, owner),
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                (float) myExplosionPower,
                false,
                Level.ExplosionInteraction.MOB
        );

        this.discard();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putFloat("DirectHitDamage", directHitDamage);
        nbt.putFloat("ExtraExplosionDamage", extraExplosionDamage);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("DirectHitDamage")) directHitDamage = nbt.getFloat("DirectHitDamage");
        if (nbt.contains("ExtraExplosionDamage")) extraExplosionDamage = nbt.getFloat("ExtraExplosionDamage");
    }
}