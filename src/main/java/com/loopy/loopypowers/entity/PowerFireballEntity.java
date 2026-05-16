package com.loopy.loopypowers.entity;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class PowerFireballEntity extends LargeFireball {

    private float directHitDamage    = 6.0f;
    private float extraExplosionDamage = 0.0f;
    private Vec3  lastTrailPos       = null;
    private int   myExplosionPower   = 1;
    private Vec3  constantVelocity   = Vec3.ZERO;
    private Vec3  lastServerPos      = null;
    private int   stuckTicks         = 0;

    /* ============================================================
       CONSTRUCTORS
       ============================================================ */

    public PowerFireballEntity(EntityType<? extends LargeFireball> type, Level level) {
        super(type, level);
    }

    public PowerFireballEntity(
            EntityType<? extends LargeFireball> type,
            Level level,
            LivingEntity owner,
            double xd, double yd, double zd,
            int explosionPower
    ) {
        super(type, level);
        this.setOwner(owner);

        this.myExplosionPower  = explosionPower;
        this.constantVelocity  = new Vec3(xd, yd, zd);

        this.setDeltaMovement(this.constantVelocity);
    }

    /* ============================================================
       TUNING API
       ============================================================ */

    public void setDamageValues(float directDamage, float explosionDamage) {
        this.directHitDamage      = directDamage;
        this.extraExplosionDamage = explosionDamage;
    }

    public void setDamage(float damage) {
        this.directHitDamage      = damage;
        this.extraExplosionDamage = damage * 0.5f;
    }

    public void setExplosionPower(int power) {
        this.myExplosionPower = power;
    }

    /* ============================================================
       VELOCITY
       ============================================================ */

    @Override
    public void setDeltaMovement(@NotNull Vec3 velocity) {
        this.constantVelocity = velocity;
        super.setDeltaMovement(velocity);
    }

    /* ============================================================
       TICK
       ============================================================ */

    @Override
    public void tick() {
        if (constantVelocity != null && constantVelocity.lengthSqr() > 0.001) {
            this.setDeltaMovement(constantVelocity);
        }

        super.tick();

        Vec3 currentPos = this.position();

        if (this.level().isClientSide) {
            // CLIENT-SIDE PARTICLES: Zero network lag!
            if (lastTrailPos != null) {
                double dist  = lastTrailPos.distanceTo(currentPos);
                int    steps = (int) Math.max(1, Math.ceil(dist / 0.5));

                for (int i = 0; i <= steps; i++) {
                    double t  = (double) i / steps;
                    double px = lastTrailPos.x + (currentPos.x - lastTrailPos.x) * t;
                    double py = lastTrailPos.y + (currentPos.y - lastTrailPos.y) * t;
                    double pz = lastTrailPos.z + (currentPos.z - lastTrailPos.z) * t;

                    // Clean, optimized campfire trail
                    this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py + 0.2, pz, 0, 0.02, 0);
                    if (this.level().random.nextFloat() < 0.2f) {
                        this.level().addParticle(ParticleTypes.FLAME, px, py + 0.2, pz, 0, 0.01, 0);
                    }
                }
            }
            lastTrailPos = currentPos;
            return; // Skip server logic
        }

        // --- SERVER-SIDE PHYSICS ---

        if (lastServerPos != null && currentPos.distanceToSqr(lastServerPos) < 0.0001) {
            stuckTicks++;
            if (stuckTicks > 3) {
                explode();
                this.discard();
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastServerPos = currentPos;

        AABB box = this.getBoundingBox().inflate(0.5, 0.5, 0.5);
        for (Entity entity : this.level().getEntities(this, box)) {
            if (this.getOwner() != null && entity.getUUID().equals(this.getOwner().getUUID())) continue;
            this.onHitEntity(new EntityHitResult(entity));
            return;
        }
    }

    /* ============================================================
       HIT HANDLING
       ============================================================ */

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            explode();
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            explode();
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (this.level().isClientSide) return;

        Entity hitEntity = entityHitResult.getEntity();
        Entity owner     = this.getOwner();

        DamageSource directSrc = this.damageSources().magic();
        DamageSource splashSrc = this.damageSources().explosion(this, owner);

        double radius    = 3.5;
        AABB   splashBox = new AABB(this.position(), this.position()).inflate(radius);

        for (Entity e : this.level().getEntities(this, splashBox)) {
            if (owner != null && e.getUUID().equals(owner.getUUID())) continue;

            if (e instanceof LivingEntity living) {
                if (e == hitEntity) {
                    living.hurt(directSrc, directHitDamage + extraExplosionDamage);
                    living.setRemainingFireTicks(100);

                    if (owner instanceof ServerPlayer ownerPlayer) {
                        CameraShake.shakeNearby(ownerPlayer, 12.0, 8, 0.8f);
                    }
                } else {
                    double dist    = e.position().distanceTo(this.position());
                    double falloff = 1.0 - (dist / radius);
                    if (falloff > 0) {
                        living.hurt(splashSrc, (float)(extraExplosionDamage * falloff));
                        living.setRemainingFireTicks(60);
                    }
                }
            }
        }

        this.discard();
    }

    private void explode() {
        if (!this.level().isClientSide) {
            ServerLevel sw = (ServerLevel) this.level();

            // explode
            sw.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

            sw.explode(this, this.getX(), this.getY(), this.getZ(), myExplosionPower, Level.ExplosionInteraction.NONE);
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putFloat("DirectHitDamage",      directHitDamage);
        nbt.putFloat("ExtraExplosionDamage", extraExplosionDamage);
        nbt.putInt("ExplosionPower",         myExplosionPower);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("DirectHitDamage"))      directHitDamage      = nbt.getFloat("DirectHitDamage");
        if (nbt.contains("ExtraExplosionDamage")) extraExplosionDamage = nbt.getFloat("ExtraExplosionDamage");
        if (nbt.contains("ExplosionPower"))        myExplosionPower     = nbt.getInt("ExplosionPower");
    }
}