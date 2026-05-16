package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.effect.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DisplaceEntity extends Entity {

    private ServerPlayer owner;
    private int life;
    private final Set<UUID> hit = new HashSet<>();

    public DisplaceEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true; // The 1.21.1 equivalent of noClip
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

        Vec3 prev = this.position();
        Vec3 next = prev.add(this.getDeltaMovement());

        this.setPos(next.x, next.y, next.z);

        // particles
        if (this.level() instanceof ServerLevel sw) {

            Vec3 vel = this.getDeltaMovement();
            double speed = vel.length();
            Vec3 dir = speed > 1.0e-6 ? vel.normalize() : new Vec3(0, 0, 1);

            // colours
            DustParticleOptions darkBlue = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f), 0.8f);
            DustParticleOptions midBlue = new DustParticleOptions(new Vector3f(0.2f, 0.4f, 1.0f), 0.6f);
            DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1.0f), 0.4f);

            // core
            sw.sendParticles(brightBlue, this.getX(), this.getY(), this.getZ(), 2, 0.02, 0.02, 0.02, 0.01);
            sw.sendParticles(midBlue, this.getX(), this.getY(), this.getZ(), 3, 0.05, 0.05, 0.05, 0.02);

            int zaps = 1 + sw.random.nextInt(2); // 1 to 2 distinct lightning arcs per tick

            for (int z = 0; z < zaps; z++) {
                // arc slightly off-centre
                Vec3 startPos = this.position().add(
                        (sw.random.nextDouble() - 0.5) * 0.1,
                        (sw.random.nextDouble() - 0.5) * 0.1,
                        (sw.random.nextDouble() - 0.5) * 0.1
                );

                // random direction
                Vec3 baseDir = new Vec3(
                        sw.random.nextGaussian(),
                        sw.random.nextGaussian(),
                        sw.random.nextGaussian()
                ).normalize();

                // bias opposite to velocity
                if (baseDir.dot(dir) > 0.3) {
                    baseDir = baseDir.scale(-0.5)
                            .add(sw.random.nextGaussian() * 0.5, sw.random.nextGaussian() * 0.5, sw.random.nextGaussian() * 0.5)
                            .normalize();
                }

                Vec3 current = startPos;
                int segments = 2 + sw.random.nextInt(3); // 2 to 4 segments per zap

                for (int i = 0; i < segments; i++) {
                    // jitter
                    Vec3 stepDir = baseDir.add(
                            sw.random.nextGaussian() * 0.3,
                            sw.random.nextGaussian() * 0.3,
                            sw.random.nextGaussian() * 0.3
                    ).normalize().scale(0.1 + sw.random.nextDouble() * 0.2); // Shorter step length

                    Vec3 nextStep = current.add(stepDir);

                    // interpolate particles along the segment
                    double dist = current.distanceTo(nextStep);
                    int subSteps = (int) Math.max(2, Math.ceil(dist / 0.15));

                    for (int s = 0; s <= subSteps; s++) {
                        double t = (double) s / subSteps;
                        double px = current.x + (nextStep.x - current.x) * t;
                        double py = current.y + (nextStep.y - current.y) * t;
                        double pz = current.z + (nextStep.z - current.z) * t;

                        // Mix bright and mid blues for the arc
                        DustParticleOptions sparkCol = sw.random.nextFloat() < 0.4f ? brightBlue : midBlue;
                        sw.sendParticles(sparkCol, px, py, pz, 1, 0, 0, 0, 0);

                        // Throw out occasional dark matter sparks (less often)
                        if (sw.random.nextFloat() < 0.1f) {
                            sw.sendParticles(darkBlue, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }

                    current = nextStep;
                    baseDir = stepDir.normalize(); // carry momentum to next segment
                }
            }
        }

        // hit
        AABB sweep = new AABB(prev, next).inflate(0.7);

        List<LivingEntity> targets = this.level().getEntitiesOfClass(
                LivingEntity.class,
                sweep,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity t : targets) {
            if (hit.contains(t.getUUID())) continue;

            var opt = t.getBoundingBox().inflate(0.25).clip(prev, next);
            if (opt.isEmpty()) continue;

            hit.add(t.getUUID());

            // apply effect
            t.addEffect(new MobEffectInstance(ModEffects.DISPLACED, 120, 0, false, false, false));
        }
    }

    /* ============================================================
       Data
       ============================================================ */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {}
}