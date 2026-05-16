package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.network.payload.BlackHoleParticlePayload;
import com.loopy.loopypowers.power.CosmicPower;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;

public class BlackHoleEntity extends Entity {

    private ServerPlayer owner;
    private int life;
    private Vec3 travelDirection = Vec3.ZERO;

    /* ============================================================
       Constants
       ============================================================ */

    public static final int   LIFESPAN         = 200;  // ticks
    private static final double TRAVEL_SPEED   = 0.03;
    private static final float MOOVIN_CHANCE   = 0.0003f;

    public static final double OUTER_RADIUS    = 25.0;
    public static final double MID_RADIUS      = 16.0;
    public static final double INNER_RADIUS    = 4.0;

    public static final double OUTER_PULL      = 0.02;
    public static final double MID_PULL        = 0.7;
    public static final double INNER_PULL      = 0.17;

    private static final double MAX_PULL_SPEED = 0.45;
    private static final double ORBIT_TANGENT_MIX = 0.55;

    public static final float INNER_DAMAGE_PER_TICK = 1.3f;

    // Particles (Restored to original density since it's client-side now!)
    private static final int   CORE_RINGS       = 8;
    private static final int   DISC_ARMS        = 4;
    private static final float DISC_ARM_RADIUS  = 6.0f;
    private static final int   OUTER_WISP_COUNT = 12;

    private static final DustParticleOptions BLACK =
            new DustParticleOptions(new Vector3f(0.02f, 0.0f, 0.05f), 2.8f);

    private static final DustParticleOptions PURPLE =
            new DustParticleOptions(new Vector3f(0.25f, 0.0f, 0.4f), 1.5f);

    private static final DustParticleOptions ORANGE =
            new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.0f), 1.5f);

    private static final DustParticleOptions HOT_ORANGE =
            new DustParticleOptions(new Vector3f(1.0f, 0.65f, 0.1f), 1.55f);

    private static final DustParticleOptions LIGHT_PURPLE =
            new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.7f), 1.55f);

    /* ============================================================
       Constructor
       ============================================================ */

    public BlackHoleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public void setOwner(ServerPlayer owner) {
        this.owner = owner;
    }

    public ServerPlayer getOwner() {
        return owner;
    }

    public void setTravelDirection(Vec3 dir) {
        this.travelDirection = dir.normalize();
    }

    /* ============================================================
       Tick
       ============================================================ */

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        life++;
        if (life > LIFESPAN || owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }

        Vec3 current = this.position();
        Vec3 newPos = current.add(this.travelDirection.scale(TRAVEL_SPEED));
        this.setPos(newPos.x, newPos.y, newPos.z);

        ServerLevel world = (ServerLevel) this.level();
        Vec3 center = this.position();
        float lifeProgress = (float) life / LIFESPAN;

        boolean doingDamage = pullAndDamageEntities(world, center);

        // -- EGG --
        if (!doingDamage && world.random.nextFloat() < MOOVIN_CHANCE) {
            Cow moovin = EntityType.COW.create(world);
            if (moovin != null) {
                double ox = (world.random.nextDouble() - 0.5) * 12.0;
                double oy = (world.random.nextDouble() - 0.5) * 12.0;
                double oz = (world.random.nextDouble() - 0.5) * 12.0;
                moovin.moveTo(center.x + ox, center.y + oy, center.z + oz, world.random.nextFloat() * 360f, 0);
                moovin.setCustomName(Component.literal("Moovin"));
                world.addFreshEntity(moovin);
            }
        }

        // Send payload to clients instead of calculating particles on the server
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                this,
                new BlackHoleParticlePayload(this.getId(), lifeProgress)
        );

        if (life % 30 == 0) {
            playBlackHoleLoop(world, center);
        }
    }

    /* ============================================================
       pull stuff
       ============================================================ */

    private boolean pullAndDamageEntities(ServerLevel world, Vec3 center) {
        boolean dealtDamage = false;

        List<Entity> nearby = world.getEntitiesOfClass(
                Entity.class,
                new AABB(center, center).inflate(OUTER_RADIUS),
                e -> e.isAlive() && e != owner && e != this && !e.isSpectator()
        );

        for (Entity e : nearby) {
            Vec3 toCenter = center.subtract(e.position());
            double dist = toCenter.length();
            if (dist < 0.01) continue;

            if (dist <= INNER_RADIUS) {
                applyOrbitalPull(e, toCenter, dist, INNER_PULL);

                if (e instanceof LivingEntity living) {
                    applyInnerRingEffects(world, living);
                    dealtDamage = true;
                }

            } else if (dist <= MID_RADIUS) {
                applyOrbitalPull(e, toCenter, dist, MID_PULL);

            } else {
                applyOrbitalPull(e, toCenter, dist, OUTER_PULL);
            }
        }

        return dealtDamage;
    }

    private void applyOrbitalPull(Entity entity, Vec3 toCenter, double dist, double strength) {
        Vec3 inward = toCenter.normalize();
        Vec3 tangent = new Vec3(-inward.z, 0, inward.x).normalize();

        double tangentMix = ORBIT_TANGENT_MIX * Mth.clamp(dist / MID_RADIUS, 0, 1);
        Vec3 pullDir = inward.scale(1.0 - tangentMix).add(tangent.scale(tangentMix)).normalize();

        Vec3 vel = entity.getDeltaMovement();
        Vec3 newVel = vel.add(pullDir.scale(strength));

        if (newVel.length() > MAX_PULL_SPEED) {
            newVel = newVel.normalize().scale(MAX_PULL_SPEED);
        }

        entity.setDeltaMovement(newVel);
        entity.hasImpulse = true;

        if (entity.level() instanceof ServerLevel sw) {
            sw.getChunkSource().broadcastAndSend(entity,
                    new ClientboundTeleportEntityPacket(entity));
        }
    }

    private void applyInnerRingEffects(ServerLevel world, LivingEntity entity) {
        if (owner != null) {
            entity.hurt(
                    ModDamageTypes.blackHole(world, owner),
                    INNER_DAMAGE_PER_TICK
            );
        }
        CosmicPower.drainFateTimer(entity);
    }

    /* ============================================================
       Sounds
       ============================================================ */

    private void playBlackHoleLoop(ServerLevel world, Vec3 center) {
        double soundRadiusSq = OUTER_RADIUS * OUTER_RADIUS;

        for (ServerPlayer p : world.players()) {
            if (p.distanceToSqr(center) <= soundRadiusSq) {
                world.playSound(
                        null,
                        p.blockPosition(),
                        ModSounds.DARKNESSLOOP.get(),
                        SoundSource.PLAYERS,
                        0.9f,
                        0.7f
                );
            }
        }
    }

    /* ============================================================
       Particles (CLIENT ONLY NOW)
       ============================================================ */

    public void spawnClientParticles(float lifeProgress) {
        Level level = this.level();
        Vec3 center = this.position();
        long time = level.getGameTime();

        spawnEventHorizon(level, center, time);
        spawnAccretionDisk(level, center, time, lifeProgress);
        spawnInnerVortex(level, center, time);
        spawnOuterWisps(level, center, time);

        if (lifeProgress > 0.75f) {
            spawnCollapseFlare(level, center, lifeProgress, time);
        }
    }

    private void spawnEventHorizon(Level level, Vec3 center, long time) {
        for (int ring = 0; ring < CORE_RINGS; ring++) {
            double ringRadius = 0.5 + ring * 0.6;
            int pointsInRing = 6 + ring * 2;
            double rotOffset = time * (0.08 + ring * 0.015) * (ring % 2 == 0 ? 1 : -1);

            for (int j = 0; j < pointsInRing; j++) {
                double angle = rotOffset + (j * Math.PI * 2.0 / pointsInRing);
                double tiltY = Math.sin(angle * 0.5 + ring) * 0.25;

                double x = center.x + Math.cos(angle) * ringRadius;
                double y = center.y + 1.0 + tiltY;
                double z = center.z + Math.sin(angle) * ringRadius;

                DustParticleOptions color = (ring < 2) ? BLACK : PURPLE;
                level.addParticle(color, x, y, z, 0, 0, 0);
            }
        }
    }

    private void spawnAccretionDisk(Level level, Vec3 center, long time, float lifeProgress) {
        double baseSpeed = 0.06 + lifeProgress * 0.04;

        for (int arm = 0; arm < DISC_ARMS; arm++) {
            double armOffset = arm * (Math.PI * 2.0 / DISC_ARMS);

            int trailLength = 10;
            for (int t = 0; t < trailLength; t++) {
                double trailFraction = (double) t / trailLength;

                double angle = time * baseSpeed + armOffset - (t * 0.28);
                double r = 0.9 + trailFraction * (DISC_ARM_RADIUS - 0.9);
                double waveY = Math.sin(angle * 2 + arm) * 0.18 * (1.0 - trailFraction);

                double x = center.x + Math.cos(angle) * r;
                double y = center.y + 1.0 + waveY;
                double z = center.z + Math.sin(angle) * r;

                DustParticleOptions diskColor = (trailFraction < 0.4) ? ORANGE
                        : (trailFraction < 0.7) ? HOT_ORANGE
                        : LIGHT_PURPLE;

                level.addParticle(diskColor, x, y, z, 0, 0, 0);

                if (t < 3 && level.random.nextFloat() < 0.15f) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            x, y, z,
                            (level.random.nextDouble() - 0.5) * 0.05,
                            (level.random.nextDouble() - 0.5) * 0.05,
                            (level.random.nextDouble() - 0.5) * 0.05);
                }
            }
        }
    }

    private void spawnInnerVortex(Level level, Vec3 center, long time) {
        int points = 8;
        for (int i = 0; i < points; i++) {
            double angle = -(time * 0.14) + (i * Math.PI * 2.0 / points);
            double r = 0.55 + Math.sin(time * 0.1 + i) * 0.1;

            double x = center.x + Math.cos(angle) * r;
            double y = center.y + 1.0 + Math.sin(angle * 3) * 0.1;
            double z = center.z + Math.sin(angle) * r;

            level.addParticle(PURPLE, x, y, z, 0, 0, 0);
        }
    }

    private void spawnOuterWisps(Level level, Vec3 center, long time) {
        int wispIndex = (int)(time % OUTER_WISP_COUNT);

        double angle = time * 0.03 + (wispIndex * Math.PI * 2.0 / OUTER_WISP_COUNT);
        double r = OUTER_RADIUS * 0.6 + level.random.nextDouble() * OUTER_RADIUS * 0.3;

        double x = center.x + Math.cos(angle) * r;
        double y = center.y + 1.0 + (level.random.nextDouble() - 0.5) * 3.0;
        double z = center.z + Math.sin(angle) * r;

        double dx = (center.x - x) * 0.01;
        double dz = (center.z - z) * 0.01;

        level.addParticle(LIGHT_PURPLE, x, y, z, dx, 0.005, dz);
    }

    private void spawnCollapseFlare(Level level, Vec3 center, float lifeProgress, long time) {
        float intensity = (lifeProgress - 0.75f) / 0.25f;

        if (time % 3 == 0) {
            int burstCount = (int)(3 + intensity * 8);
            for (int i = 0; i < burstCount; i++) {
                double vx = (level.random.nextDouble() - 0.5) * 0.3 * intensity;
                double vy = (level.random.nextDouble() - 0.5) * 0.2 * intensity;
                double vz = (level.random.nextDouble() - 0.5) * 0.3 * intensity;
                level.addParticle(ParticleTypes.END_ROD,
                        center.x, center.y + 1.0, center.z,
                        vx, vy, vz);
            }
        }

        int coronaCount = (int)(intensity * 4);
        for (int i = 0; i < coronaCount; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = 0.5 + level.random.nextDouble() * 0.8;
            level.addParticle(ORANGE,
                    center.x + Math.cos(angle) * r,
                    center.y + 1.0 + (level.random.nextDouble() - 0.5) * 0.4,
                    center.z + Math.sin(angle) * r,
                    0, 0, 0);
        }
    }

    /* ============================================================
       Data
       ============================================================ */

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.life = nbt.getInt("Life");
        double dx = nbt.getDouble("DirX");
        double dy = nbt.getDouble("DirY");
        double dz = nbt.getDouble("DirZ");
        this.travelDirection = new Vec3(dx, dy, dz);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putInt("Life", this.life);
        nbt.putDouble("DirX", this.travelDirection.x);
        nbt.putDouble("DirY", this.travelDirection.y);
        nbt.putDouble("DirZ", this.travelDirection.z);
    }
}