package com.loopy.loopypowers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class ShadowStepEntity extends Entity {

    private ServerPlayer owner;
    private int life;

    public ShadowStepEntity(EntityType<?> type, Level level) {
        super(type, level);
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
        if (life > 40) {
            this.discard();
            return;
        }

        Vec3 prev = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 next = prev.add(vel);

        ServerLevel world = (ServerLevel) this.level();

        // clip replaces raycast
        var blockHit = world.clip(new ClipContext(
                prev,
                next,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.discard();
            return;
        }

        this.setPos(next.x, next.y, next.z);

        float radius = 0.3f;
        float speed = 0.3f;

        for (int i = 0; i < 4; i++) {
            float angle = (life * speed) + (i * (float)Math.PI / 2);

            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            world.sendParticles(
                    new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.05f), 1.0f),
                    this.getX() + offsetX,
                    this.getY() + 0.1,
                    this.getZ() + offsetZ,
                    1,
                    0, 0, 0,
                    0
            );
        }

        AABB box = new AABB(prev, next).inflate(0.5);

        List<LivingEntity> targets = world.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity target : targets) {

            var hit = target.getBoundingBox().inflate(0.3).clip(prev, next);
            if (hit.isEmpty()) continue;

            teleportBehind(target);

            // damage becomes hurt
            target.hurt(
                    world.damageSources().playerAttack(owner),
                    6.0f
            );
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));

            // ENTITY_ENDERMAN_TELEPORT -> ENDERMAN_TELEPORT
            world.playSound(null, target.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT,
                    owner.getSoundSource(),
                    0.8f, 1.2f);

            world.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    target.getX(),
                    target.getY(0.5),
                    target.getZ(),
                    20,
                    0.4, 0.5, 0.4,
                    0.02
            );

            this.discard();
            return;
        }
    }

    private void teleportBehind(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel world)) return;

        // getRotationVec -> getViewVector
        Vec3 forward = target.getViewVector(1.0f).normalize();
        Vec3 behind = target.position().subtract(forward.scale(1.5));

        BlockPos base = BlockPos.containing(behind);

        // find safe spot
        for (int y = -1; y <= 1; y++) {
            BlockPos pos = base.above(y); // up becomes above

            if (isSafe(world, pos)) {

                // In NeoForge, we update look logic before teleporting so it carries over
                faceTarget(owner, target);

                owner.teleportTo(
                        world,
                        pos.getX() + 0.5,
                        pos.getY(),
                        pos.getZ() + 0.5,
                        java.util.Set.of(),
                        owner.getYRot(),
                        owner.getXRot()
                );
                return;
            }
        }

        // fallback: teleport slightly above
        faceTarget(owner, target);
        owner.teleportTo(
                world,
                behind.x,
                behind.y + 1,
                behind.z,
                java.util.Set.of(),
                owner.getYRot(),
                owner.getXRot()
        );
    }

    private boolean isSafe(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).isAir()
                && world.getBlockState(pos.above()).isAir();
    }

    private void faceTarget(ServerPlayer player, LivingEntity target) {
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        Vec3 diff = targetPos.subtract(playerPos);

        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Math.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float pitch = (float)(-(Math.atan2(dy, horizontalDist) * (180F / Math.PI)));

        player.setYRot(yaw);
        player.setXRot(pitch);

        // No requestTeleport packet required here
        // that is actually so cool i should've used this first
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag nbt) {}
    @Override protected void addAdditionalSaveData(CompoundTag nbt) {}
}