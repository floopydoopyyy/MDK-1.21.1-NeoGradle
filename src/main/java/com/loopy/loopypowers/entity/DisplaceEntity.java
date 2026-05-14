package com.loopy.loopypowers.entity;

// import com.loopy.loopypowers.power.DimensionalPower;
import com.loopy.loopypowers.power.DimensionalPower;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class DisplaceEntity extends Entity {

    private ServerPlayer owner;
    private int life;
    private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();

    public DisplaceEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true; // replaces noClip
        this.setNoGravity(true);
    }

    public void setOwner(ServerPlayer owner) {
        this.owner = owner;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        life++;
        if (life > 70) {
            this.discard();
            return;
        }

        // getPos becomes position(), getVelocity becomes getDeltaMovement()
        Vec3 prev = this.position();
        Vec3 next = prev.add(this.getDeltaMovement());

        this.setPos(next.x, next.y, next.z);

        // particles
        if (this.level() instanceof ServerLevel sw) {

            Vec3 vel = this.getDeltaMovement();
            double speed = vel.length();

            Vec3 dir = speed > 1.0e-6 ? vel.normalize() : new Vec3(0, 0, 1);

            // core
            DustParticleOptions core = new DustParticleOptions(
                    new Vector3f(0.3f, 0.6f, 1.0f), // unified blue
                    0.85f
            );

            // tighter trail (multiply becomes scale)
            Vec3 back = dir.scale(-0.2);

            sw.sendParticles(core,
                    this.getX() + back.x,
                    this.getY() + back.y,
                    this.getZ() + back.z,
                    5,
                    0.03, 0.03, 0.03,
                    0.002
            );

            // forward
            if (speed > 0.01) {
                Vec3 ahead = dir.scale(0.35);

                sw.sendParticles(core,
                        this.getX() + ahead.x,
                        this.getY() + ahead.y,
                        this.getZ() + ahead.z,
                        2,
                        0.01, 0.01, 0.01,
                        0.0
                );
            }

            // zap stuff
            if (sw.random.nextFloat() < 0.78f) {

                // random outward direction
                Vec3 baseDir = new Vec3(
                        sw.random.nextGaussian(),
                        sw.random.nextGaussian() * 0.6,
                        sw.random.nextGaussian()
                ).normalize();

                Vec3 current = this.position();

                // create segmented zigzag
                for (int i = 0; i < 4; i++) {

                    // jitter it
                    Vec3 jitter = new Vec3(
                            sw.random.nextGaussian() * 0.25,
                            sw.random.nextGaussian() * 0.25,
                            sw.random.nextGaussian() * 0.25
                    );

                    Vec3 stepDir = baseDir.add(jitter).normalize().scale(0.25);

                    current = current.add(stepDir);

                    sw.sendParticles(
                            core,
                            current.x,
                            current.y,
                            current.z,
                            1,
                            0, 0, 0,
                            0
                    );
                }
            }
        }

        // Box becomes AABB, expand becomes inflate
        AABB sweep = new AABB(prev, next).inflate(0.7);

        // getEntitiesByClass becomes getEntitiesOfClass
        List<LivingEntity> targets = this.level().getEntitiesOfClass(
                LivingEntity.class,
                sweep,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity t : targets) {
            // getUuid becomes getUUID
            if (hit.contains(t.getUUID())) continue;

            // raycast becomes clip
            var opt = t.getBoundingBox().inflate(0.25).clip(prev, next);
            if (opt.isEmpty()) continue;

            hit.add(t.getUUID());

            // getCommandTags becomes getTags
            t.getTags().add("int_displaced_" + 60);

            // hit effect
            if (this.level() instanceof ServerLevel sw) {
                sw.sendParticles(ParticleTypes.PORTAL,
                        t.getX(), t.getY(0.5), t.getZ(),
                        10,
                        0.3, 0.5, 0.3,
                        0.05
                );
            }
        }
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag nbt) {}
    @Override protected void addAdditionalSaveData(CompoundTag nbt) {}
}