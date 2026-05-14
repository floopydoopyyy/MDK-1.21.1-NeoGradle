package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.power.PsychicPower;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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

public class PuppetryEntity extends Entity {

    private ServerPlayer owner;
    private int life;

    public static final double SPEED = 1.3;

    /* ============================================================
       Particles
       ============================================================ */

    private static final DustParticleOptions MAIN_PINK =
            new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.6f), 1.1f);

    private static final DustParticleOptions ACCENT_LIGHT =
            new DustParticleOptions(new Vector3f(1.0f, 0.6f, 0.9f), 0.8f);

    public PuppetryEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public void setOwner(ServerPlayer owner) {
        this.owner = owner;
    }

    public ServerPlayer getOwner() {
        return owner;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        life++;
        if (life > 100 || owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }

        Vec3 current = this.position();
        Vec3 next = current.add(this.getDeltaMovement());

        HitResult hit = this.level().clip(new ClipContext(
                current, next,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            this.discard();
            return;
        }

        this.setPos(next.x, next.y, next.z);

        ServerLevel world = (ServerLevel) this.level();

        // Trail particles
        world.sendParticles(MAIN_PINK, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
        world.sendParticles(ACCENT_LIGHT, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);

        // Collisions
        AABB box = this.getBoundingBox().inflate(0.5).move(this.getDeltaMovement());
        List<Entity> entities = world.getEntities(this, box, e -> e instanceof LivingEntity && e != owner && e.isAlive());

        for (Entity e : entities) {
            onHit((LivingEntity) e);
            break; // Only hit the first entity
        }
    }

    private void onHit(LivingEntity target) {
        ServerLevel world = (ServerLevel) this.level();

        PsychicPower.applyUltimateControl(owner, target);

        int points = 30;
        double radius = 1.5;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;

            world.sendParticles(MAIN_PINK,
                    this.getX() + Math.cos(angle) * radius,
                    this.getY() + 0.1,
                    this.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);

            world.sendParticles(ACCENT_LIGHT,
                    this.getX() + Math.cos(angle + 0.6) * (radius * 0.7),
                    this.getY() + 0.12,
                    this.getZ() + Math.sin(angle + 0.6) * (radius * 0.7),
                    1, 0, 0, 0, 0);
        }

        // Sound at hit entity
        world.playSound(null, target.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                target.getSoundSource(),
                0.9f, 1.2f + world.random.nextFloat() * 0.2f);

        // Sound at caster
        world.playSound(null, owner.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                owner.getSoundSource(),
                0.7f, 0.9f + world.random.nextFloat() * 0.2f);

        this.discard();
    }

    /* ============================================================
       Data
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