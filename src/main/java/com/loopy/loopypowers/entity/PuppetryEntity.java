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

public class PuppetryEntity extends Entity {

    private ServerPlayer owner;
    private int life;

    /* ============================================================
       Particles
       ============================================================ */

    private static final DustParticleOptions MAIN_PINK =
            new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.6f), 1.1f);

    private static final DustParticleOptions ACCENT_LIGHT =
            new DustParticleOptions(new Vector3f(1.0f, 0.6f, 0.9f), 0.8f);

    private static final DustParticleOptions ACCENT_DARK =
            new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.6f), 0.9f);

    private static final DustParticleOptions DEEP_PURPLE =
            new DustParticleOptions(new Vector3f(0.4f, 0.0f, 0.8f), 1.3f);

    private static final DustParticleOptions MID_PURPLE =
            new DustParticleOptions(new Vector3f(0.7f, 0.1f, 0.9f), 1.0f);

    private static final DustParticleOptions BRIGHT_PINK =
            new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.9f), 0.7f);

    /* ============================================================
       Constants
       ============================================================ */

    // Lifespan
    static final int MAX_LIFE = 100;

    // Movement
    public static final double SPEED     = 0.7;
    static final double STEERING         = 0.25;

    // Targeting
    static final double TARGET_DISTANCE    = 50.0;  // further = more vertical control
    static final double DOWNWARD_SOFTENING = 0.90;  // gentle — only slightly resists diving

    // Collision
    static final double ENTITY_HIT_EXPAND    = 0.4;
    static final double PROJECTILE_BOX_EXPAND = 0.6;

    // Particles
    static final float PARTICLE_RADIUS     = 0.25f;  // outer spiral radius
    static final float PARTICLE_INNER_RADIUS = 0.08f; // inner core spiral radius
    static final float PARTICLE_LENGTH     = 0.25f;  // spacing between spiral rings
    static final float PARTICLE_SPEED      = 1.9f;   // spin rate

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

    /* ============================================================
       Tick
       ============================================================ */

    @Override
    public void tick() {
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
           Block collision
           ============================= */

        HitResult blockHit = world.clip(new ClipContext(
                prev, next,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.discard();
            return;
        }

        /* =============================
           Movement — steers toward crosshair
           ============================= */

        Vec3 look = owner.getViewVector(1.0f);
        Vec3 targetPos = owner.getEyePosition().add(look.scale(TARGET_DISTANCE));
        Vec3 toTarget = targetPos.subtract(this.position());

        if (toTarget.lengthSqr() > 0.000001) toTarget = toTarget.normalize();

        Vec3 newVel = this.getDeltaMovement()
                .scale(1.0 - STEERING)
                .add(toTarget.scale(STEERING))
                .normalize()
                .scale(SPEED);

        // Only gently resist extreme downward angles
        if (newVel.y < -0.5) {
            newVel = new Vec3(newVel.x, -0.5 + (newVel.y + 0.5) * DOWNWARD_SOFTENING, newVel.z)
                    .normalize().scale(SPEED);
        }

        this.setDeltaMovement(newVel);
        this.setPos(this.getX() + newVel.x, this.getY() + newVel.y, this.getZ() + newVel.z);

        /* =============================
           Particles — Optimized & Compressed
           ============================= */

        Vec3 dir = newVel.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        Vec3 up = dir.cross(side).normalize();

        // 1. Double Helix (reduced to 3 segments for compression)
        for (int i = 0; i < 3; i++) {
            float base = (life * PARTICLE_SPEED) + (i * (float)(Math.PI * 2.0 / 3.0));
            double back = -i * PARTICLE_LENGTH;

            // Strand A
            Vec3 posA = this.position()
                    .add(dir.scale(back))
                    .add(side.scale(Math.cos(base) * PARTICLE_RADIUS))
                    .add(up.scale(Math.sin(base) * PARTICLE_RADIUS));

            // Strand B (opposite side)
            double baseB = base + Math.PI;
            Vec3 posB = this.position()
                    .add(dir.scale(back))
                    .add(side.scale(Math.cos(baseB) * PARTICLE_RADIUS))
                    .add(up.scale(Math.sin(baseB) * PARTICLE_RADIUS));

            world.sendParticles(DEEP_PURPLE, posA.x, posA.y, posA.z, 1, 0, 0, 0, 0);
            world.sendParticles(MID_PURPLE,  posB.x, posB.y, posB.z, 1, 0.02, 0.02, 0.02, 0);
        }

        // 2. Inner tight core (counter-spinning, reduced to 2 segments)
        for (int i = 0; i < 2; i++) {
            float base = -(life * PARTICLE_SPEED * 1.5f) + (i * (float)Math.PI);
            double back = -i * PARTICLE_LENGTH * 0.8;

            Vec3 pos = this.position()
                    .add(dir.scale(back))
                    .add(side.scale(Math.cos(base) * PARTICLE_INNER_RADIUS))
                    .add(up.scale(Math.sin(base) * PARTICLE_INNER_RADIUS));

            world.sendParticles(BRIGHT_PINK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        // 3. Core line (reduced to 4 segments)
        for (int i = 0; i < 4; i++) {
            Vec3 pos = this.position().subtract(dir.scale(i * 0.25));
            world.sendParticles(MAIN_PINK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        // 4. Leading tip
        Vec3 tip = this.position().add(dir.scale(0.3));
        world.sendParticles(DEEP_PURPLE,  tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.01);
        world.sendParticles(ACCENT_LIGHT, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.01);

        /* =============================
           Entity collision
           ============================= */

        AABB box = new AABB(prev, next).inflate(PROJECTILE_BOX_EXPAND);

        List<LivingEntity> targets = world.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity target : targets) {
            Optional<Vec3> hit = target.getBoundingBox().inflate(ENTITY_HIT_EXPAND).clip(prev, next);
            if (hit.isEmpty()) continue;

            onHit(target, world);
            this.setDeltaMovement(Vec3.ZERO);
            this.discard();
            return;
        }
    }

    /* ============================================================
       Hit logic
       ============================================================ */

    private void onHit(LivingEntity target, ServerLevel world) {
        if (owner == null) return;

        PsychicPower.applyUltimateControl(this.owner, target);

        // Spiral burst around target
        float radius = 0.25f;
        float pspeed = 0.6f;

        for (int i = 0; i < 6; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 3);
            double tY = target.getY() + target.getBbHeight() * 0.5;

            world.sendParticles(MAIN_PINK,
                    target.getX() + Math.cos(angle) * radius,
                    tY,
                    target.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);

            world.sendParticles(ACCENT_LIGHT,
                    target.getX() + Math.cos(angle + 0.5) * (radius * 0.6),
                    tY + 0.1,
                    target.getZ() + Math.sin(angle + 0.5) * (radius * 0.6),
                    1, 0, 0, 0, 0);
        }

        // Impact burst
        world.sendParticles(ACCENT_DARK,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                15, 0.3, 0.4, 0.3, 0.02);

        // FX ring at projectile position
        for (int i = 0; i < 4; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 2);

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