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
import java.util.Optional;

public class CompelEntity extends Entity {

    private ServerPlayer owner;
    private int life;

    /* ============================================================
       Particle creation
       ============================================================ */

    private static final DustParticleOptions MAIN_PINK =
            new DustParticleOptions(
                    new Vector3f(0.9f, 0.2f, 0.6f), // strong pink
                    1.1f
            );

    private static final DustParticleOptions ACCENT_LIGHT =
            new DustParticleOptions(
                    new Vector3f(1.0f, 0.6f, 0.9f), // soft glow pink
                    0.8f
            );

    private static final DustParticleOptions ACCENT_DARK =
            new DustParticleOptions(
                    new Vector3f(0.5f, 0.0f, 0.6f), // magenta
                    0.9f
            );

    // CONSTANTS
    // Lifespan
    static final int MAX_LIFE = 55;

    // Movement
    static final double SPEED = 1.1;
    static final double STEERING = 0.18;

    // Wobble
    static final double WOBBLE_STRENGTH = 0.03;
    static final double WOBBLE_FREQUENCY = 0.3;

    // Targeting
    static final double TARGET_DISTANCE = 40.0;  // further = more vertical control
    static final double DOWNWARD_SOFTENING = 0.85; // gentle - only slightly resists diving

    // Collision
    static final double ENTITY_HIT_EXPAND = 0.3;
    static final double PROJECTILE_BOX_EXPAND = 0.5;

    // Particles
    static final float PARTICLE_RADIUS = 0.12f;
    static final float PARTICLE_LENGTH = 0.4f;
    static final float PARTICLE_SPEED = 0.6f;
    static final int PARTICLE_SEGMENTS = 3;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */

    public CompelEntity(EntityType<?> type, Level level) {
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
       TICK
       ============================================================ */

    @Override
    public void tick() {
        // delete if owner gone
        if (owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }

        super.tick();

        if (this.level().isClientSide) return;

        life++;
        if (life > MAX_LIFE) {
            this.discard();
            return;
        }

        ServerLevel world = (ServerLevel) this.level();

        Vec3 prev = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 next = prev.add(vel);

        /* =============================
           BLOCK COLLISION
           ============================= */

        HitResult blockHit = world.clip(new ClipContext(
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

        /* =============================
           MOVE
           ============================= */

        Vec3 look = owner.getViewVector(1.0f);
        Vec3 targetPos = owner.getEyePosition().add(look.scale(TARGET_DISTANCE));

        Vec3 toTarget = targetPos.subtract(this.position());
        double dist = toTarget.length();
        if (dist > 0.001) toTarget = toTarget.normalize();

        Vec3 currentVel = this.getDeltaMovement();

        // Constant steering — blend current direction toward target direction
        Vec3 newVel = currentVel.scale(1.0 - STEERING)
                .add(toTarget.scale(STEERING));

        // Wobble
        double wobbleX = Math.sin(life * WOBBLE_FREQUENCY) * WOBBLE_STRENGTH;
        double wobbleZ = Math.cos(life * WOBBLE_FREQUENCY) * WOBBLE_STRENGTH;
        newVel = newVel.add(wobbleX, 0, wobbleZ);

        // Normalize to constant speed
        newVel = newVel.normalize().scale(SPEED);

        // Soften downward: Only gently resist extreme downward angles
        if (newVel.y < -0.5) {
            newVel = new Vec3(newVel.x, -0.5 + (newVel.y + 0.5) * DOWNWARD_SOFTENING, newVel.z);
            newVel = newVel.normalize().scale(SPEED);
        } else if (newVel.y < -0.2) {
            newVel = new Vec3(newVel.x, newVel.y * DOWNWARD_SOFTENING, newVel.z);
        }

        this.setDeltaMovement(newVel);
        this.setPos(this.getX() + newVel.x, this.getY() + newVel.y, this.getZ() + newVel.z);

        /* =============================
           PARTICLES
           ============================= */

        Vec3 dir = newVel.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0); // fallback if perfectly vertical
        Vec3 up = new Vec3(0, 1, 0);

        float radius = PARTICLE_RADIUS;
        float length = PARTICLE_LENGTH;
        float pspeed = PARTICLE_SPEED;

        // spiral wrapped around velocity direction
        for (int i = 0; i < PARTICLE_SEGMENTS; i++) {

            float angle = (life * pspeed) + (i * 2.0f);

            double sideOffset = Math.cos(angle) * radius;
            double upOffset = Math.sin(angle) * radius;
            double backOffset = -i * length;

            Vec3 pos = this.position()
                    .add(dir.scale(backOffset))
                    .add(side.scale(sideOffset))
                    .add(up.scale(upOffset));

            world.sendParticles(MAIN_PINK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.sendParticles(ACCENT_LIGHT, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0);
        }

        // core line
        for (int i = 0; i < 3; i++) {
            Vec3 pos = this.position().subtract(dir.scale(i * 0.25));
            world.sendParticles(MAIN_PINK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        /* =============================
           ENTITY COLLISION
           ============================= */

        AABB box = new AABB(prev, next).inflate(PROJECTILE_BOX_EXPAND);

        List<LivingEntity> targets = world.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity target : targets) {
            Optional<Vec3> hit = target.getBoundingBox()
                    .inflate(ENTITY_HIT_EXPAND)
                    .clip(prev, next);

            if (hit.isEmpty()) continue;

            onHit(target, world);
            this.setDeltaMovement(Vec3.ZERO);
            this.discard();
            return;
        }
    }

    /* ============================================================
       HIT LOGIC
       ============================================================ */

    private void onHit(LivingEntity target, ServerLevel world) {

        if (owner == null) return;

        PsychicPower.applyCompel(this.owner, target);

        float radius = 0.25f;
        float pspeed = 0.6f;

        // spiral burst around target
        for (int i = 0; i < 6; i++) {

            float angle = (life * pspeed) + (i * (float) Math.PI / 3);

            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            world.sendParticles(
                    MAIN_PINK,
                    target.getX() + offsetX,
                    target.getY(0.5),
                    target.getZ() + offsetZ,
                    1,
                    0, 0, 0,
                    0
            );

            double offsetX2 = Math.cos(angle + 0.5) * (radius * 0.6);
            double offsetZ2 = Math.sin(angle + 0.5) * (radius * 0.6);

            world.sendParticles(
                    ACCENT_LIGHT,
                    target.getX() + offsetX2,
                    target.getY(0.5) + 0.1,
                    target.getZ() + offsetZ2,
                    1,
                    0, 0, 0,
                    0
            );
        }

        // impact burst
        world.sendParticles(
                ACCENT_DARK,
                target.getX(),
                target.getY(0.5),
                target.getZ(),
                15,
                0.3, 0.4, 0.3,
                0.02
        );

        // Sound at the hit entity
        world.playSound(
                null,
                target.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                target.getSoundSource(),
                0.9f,
                1.2f + world.random.nextFloat() * 0.2f
        );

        // Sound at the caster
        world.playSound(
                null,
                owner.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                owner.getSoundSource(),
                0.7f,
                0.9f + world.random.nextFloat() * 0.2f
        );
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