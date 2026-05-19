package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.network.payload.CompelHitPayload;
import com.loopy.loopypowers.network.payload.CompelTickPayload;
import com.loopy.loopypowers.power.PsychicPower;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

public class CompelEntity extends Entity {

    private ServerPlayer owner;
    private int life;

    /* ============================================================
       Constants
       ============================================================ */

    // Lifespan
    static final int MAX_LIFE = 55;

    // Movement
    static final double SPEED    = 1.1;
    static final double STEERING = 0.18;

    // Wobble
    static final double WOBBLE_STRENGTH  = 0.03;
    static final double WOBBLE_FREQUENCY = 0.3;

    // Targeting
    static final double TARGET_DISTANCE    = 40.0;
    static final double DOWNWARD_SOFTENING = 0.85;

    // Collision
    static final double ENTITY_HIT_EXPAND    = 0.3;
    static final double PROJECTILE_BOX_EXPAND = 0.5;

    // Particles — kept here as reference; actual rendering lives in PsychicFxClient
    static final float PARTICLE_RADIUS   = 0.12f;
    static final float PARTICLE_LENGTH   = 0.4f;
    static final float PARTICLE_SPEED    = 0.6f;
    static final int   PARTICLE_SEGMENTS = 3;

    /* ============================================================
       Constructor
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
        Vec3 vel  = this.getDeltaMovement();
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

        Vec3 look      = owner.getViewVector(1.0f);
        Vec3 targetPos = owner.getEyePosition().add(look.scale(TARGET_DISTANCE));
        Vec3 toTarget  = targetPos.subtract(this.position());

        if (toTarget.length() > 0.001) toTarget = toTarget.normalize();

        Vec3 newVel = this.getDeltaMovement()
                .scale(1.0 - STEERING)
                .add(toTarget.scale(STEERING));

        // Wobble
        double wobbleX = Math.sin(life * WOBBLE_FREQUENCY) * WOBBLE_STRENGTH;
        double wobbleZ = Math.cos(life * WOBBLE_FREQUENCY) * WOBBLE_STRENGTH;
        newVel = newVel.add(wobbleX, 0, wobbleZ);

        newVel = newVel.normalize().scale(SPEED);

        if (newVel.y < -0.5) {
            newVel = new Vec3(newVel.x, -0.5 + (newVel.y + 0.5) * DOWNWARD_SOFTENING, newVel.z)
                    .normalize().scale(SPEED);
        } else if (newVel.y < -0.2) {
            newVel = new Vec3(newVel.x, newVel.y * DOWNWARD_SOFTENING, newVel.z);
        }

        this.setDeltaMovement(newVel);
        this.setPos(this.getX() + newVel.x, this.getY() + newVel.y, this.getZ() + newVel.z);

        /* =============================
           Particles — dispatched to client via payload
           ============================= */

        PacketDistributor.sendToPlayersTrackingEntity(this, new CompelTickPayload(
                this.getX(), this.getY(), this.getZ(),
                newVel.x, newVel.y, newVel.z,
                life
        ));

        /* =============================
           Entity collision
           ============================= */

        AABB box = new AABB(prev, next).inflate(PROJECTILE_BOX_EXPAND);

        List<LivingEntity> targets = world.getEntitiesOfClass(
                LivingEntity.class, box,
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
       Hit logic
       ============================================================ */

    private void onHit(LivingEntity target, ServerLevel world) {
        if (owner == null) return;

        PsychicPower.applyCompel(this.owner, target);

        // Dispatch hit particles to all nearby clients
        PacketDistributor.sendToPlayersTrackingEntity(target, new CompelHitPayload(
                target.getId(),
                life
        ));

        // Sounds remain server-side
        world.playSound(null, target.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                target.getSoundSource(),
                0.9f, 1.2f + world.random.nextFloat() * 0.2f);

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