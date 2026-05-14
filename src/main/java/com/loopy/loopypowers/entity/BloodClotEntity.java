package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.power.BloodPower;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Projectile that shoots when using the Blood Clot ability.
 * Hooked into BloodPower for the "Pop" and "Bleed" mechanics.
 */
public class BloodClotEntity extends Projectile {

    private int life = 0;
    private int maxLife = 60;
    private int slowTicks = 70;
    private int weakTicks = 70;
    private float hitDamage = 2.0f;

    private static final DustParticleOptions BLOOD_DUST =
            new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.05f), 1.2f);

    public BloodClotEntity(EntityType<? extends BloodClotEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true); // Ensures it flies straight
    }

    public void setTuning(int maxLife, int slowTicks, int weakTicks, float hitDamage) {
        this.maxLife = maxLife;
        this.slowTicks = slowTicks;
        this.weakTicks = weakTicks;
        this.hitDamage = hitDamage;
    }

    @Override
    public void tick() {
        Vec3 start = this.position();
        Vec3 v = this.getDeltaMovement();
        Vec3 end = start.add(v);

        super.tick();

        if (this.isRemoved()) return;

        // Particles (Server-side check)
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sw) {
            Vec3 p = this.position();

            // Main trail particles
            sw.sendParticles(BLOOD_DUST, p.x, p.y, p.z, 4, 0.05, 0.05, 0.05, 0.0);
            sw.sendParticles(BLOOD_DUST, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.0);

            if (this.random.nextFloat() < 0.25f) {
                sw.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }

        life++;
        if (life >= maxLife) {
            splat();
            return;
        }

        // Entity Collision (Manual sweep to ensure accuracy)
        AABB sweepBox = this.getBoundingBox().expandTowards(v).inflate(1.2);
        LivingEntity hitTarget = null;
        double closestDist = Double.MAX_VALUE;

        // getOtherEntities becomes getEntities
        List<Entity> list = this.level().getEntities(this, sweepBox, this::canHitEntity);
        for (Entity e : list) {
            double d = start.distanceToSqr(e.position());
            if (d < closestDist) {
                closestDist = d;
                hitTarget = (LivingEntity) e;
            }
        }

        if (hitTarget != null) {
            this.onHit(new EntityHitResult(hitTarget));
            return;
        }

        // Block Collision (Raycast becomes clip)
        HitResult blockHit = this.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            splat();
            return;
        }

        // Apply movement
        this.setPos(end.x, end.y, end.z);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity.isSpectator()) return false;
        Entity owner = this.getOwner();
        return owner == null || entity != owner;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (hitResult instanceof EntityHitResult ehr) {
            onHitEntity(ehr);
        } else {
            splat();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult ehr) {
        if (!(ehr.getEntity() instanceof LivingEntity target)) {
            splat();
            return;
        }

        final LivingEntity owner = (this.getOwner() instanceof LivingEntity le) ? le : null;

        // Apply direct hit damage
        if (hitDamage > 0.0f) {
            // Using modern hurt() and damageSources()
            DamageSource src = (owner != null)
                    ? owner.damageSources().magic()
                    : target.damageSources().magic();
            target.hurt(src, hitDamage);
        }

        // Logic hooked into BloodPower
        if (!this.level().isClientSide && owner instanceof ServerPlayer sp) {
            if (BloodPower.isBleeding(target)) {
                // If target is bleeding, consume the bleed for burst damage
                BloodPower.popBleed(target, sp);
            } else {
                // Otherwise, apply debuffs and start bleed
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 1, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weakTicks, 0, true, true));

                // Static methods in BloodPower handle the attribute application
                BloodPower.applyBleedFromProjectile(
                        sp, target,
                        BloodPower.CLOT_BLEED_DAMAGE,
                        BloodPower.CLOT_BLEED_DURATION,
                        BloodPower.CLOT_BLEED_INTERVAL
                );
            }
        }

        splat();
    }

    private void splat() {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sw) {
            sw.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    this.getX(), this.getY(), this.getZ(),
                    8, 0.25, 0.20, 0.25, 0.02);
        }
        this.discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Required for 1.21.1
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        life = nbt.getInt("Life");
        maxLife = nbt.getInt("MaxLife");
        slowTicks = nbt.getInt("SlowTicks");
        weakTicks = nbt.getInt("WeakTicks");
        hitDamage = nbt.getFloat("HitDamage");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putInt("Life", life);
        nbt.putInt("MaxLife", maxLife);
        nbt.putInt("SlowTicks", slowTicks);
        nbt.putInt("WeakTicks", weakTicks);
        nbt.putFloat("HitDamage", hitDamage);
    }
}