package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.*;
import com.loopy.loopypowers.sound.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

public class ElementalRitual implements RitualInterface {

    /* ============================================================
        POWER POOL
       ============================================================ */

    private static final List<Supplier<PowerInterface>> ELEMENTAL_POWERS = List.of(
            FirePower::new,
            IcePower::new,
            LightningPower::new
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private static final double COLUMN_HEIGHT = 10.0;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // fire
    private static final DustParticleOptions FIRE_ORANGE =
            new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.0f), 1.5f);
    private static final DustParticleOptions FIRE_RED =
            new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.0f), 1.3f);

    // water
    private static final DustParticleOptions ICE_BLUE =
            new DustParticleOptions(new Vector3f(0.35f, 0.8f, 1.0f), 1.4f);
    private static final DustParticleOptions ICE_WHITE =
            new DustParticleOptions(new Vector3f(0.8f, 0.95f, 1.0f), 1.1f);

    // Lightning
    private static final DustParticleOptions LIGHTNING_YELLOW =
            new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.1f), 1.5f);
    private static final DustParticleOptions LIGHTNING_WHITE =
            new DustParticleOptions(new Vector3f(0.9f, 0.95f, 1.0f), 1.2f);

    // Earth
    private static final DustParticleOptions EARTH_BROWN =
            new DustParticleOptions(new Vector3f(0.45f, 0.28f, 0.08f), 1.4f);
    private static final DustParticleOptions EARTH_GREEN =
            new DustParticleOptions(new Vector3f(0.2f, 0.55f, 0.1f), 1.2f);

    // Water2
    private static final DustParticleOptions WATER_TEAL =
            new DustParticleOptions(new Vector3f(0.1f, 0.6f, 0.8f), 1.3f);
    private static final DustParticleOptions WATER_CYAN =
            new DustParticleOptions(new Vector3f(0.4f, 0.9f, 0.9f), 1.0f);

    // Air
    private static final DustParticleOptions AIR_PALE =
            new DustParticleOptions(new Vector3f(0.85f, 0.95f, 1.0f), 0.9f);

    // Column definitions — each has a primary and secondary particle.
    private static final DustParticleOptions[] COL_PRIMARY   =
            { FIRE_ORANGE, ICE_BLUE, LIGHTNING_YELLOW, WATER_TEAL };
    private static final DustParticleOptions[] COL_SECONDARY =
            { FIRE_RED,    ICE_WHITE, LIGHTNING_WHITE,  WATER_CYAN };

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public ElementalRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    public boolean tick(ServerLevel world) {
        // if they die or disconnect mid-ritual, clean up and cancel it
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayer sp) {
                cancelRitual(sp);
            }
            return true;
        }

        if (!(player instanceof ServerPlayer sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        //  ambient loop plays evenly throughout the entire ritual
        if (ticks == 1 || ticks % 28 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALLOOP.get(), SoundSource.PLAYERS, 0.5f, 0.7f);
        }

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        if (stage < 4) lockPosition(sp);

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

        if (ticks >= TOTAL_TICKS) {
            onComplete(sp, world);
            return true;
        }

        return false;
    }

    /* ============================================================
       STAGE HELPERS
       ============================================================ */

    private int currentStage() {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) return 3;
        return 4;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    private void lockPosition(ServerPlayer sp) {
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;
        sp.fallDistance = 0;
        sp.setOnGround(true);
        sp.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 5, 10, true, false, false));
    }

    private void cancelRitual(ServerPlayer sp) {
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

            world.playSound(null, sp.blockPosition(),
                    SoundEvents.MOSS_PLACE,        SoundSource.PLAYERS, 1.0f, 0.7f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WATER_AMBIENT,     SoundSource.PLAYERS, 0.6f, 1.0f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos = sp.position();
        long time = world.getGameTime();

        // Earth ring (Not Urath)
        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2.0 * i / 6 + time * 0.02;
                double r = 2.5 + Math.sin(time * 0.05 + i) * 0.3;
                world.sendParticles(EARTH_BROWN,
                        pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r,
                        1, 0.1, 0.04, 0.1, 0.005);
                world.sendParticles(EARTH_GREEN,
                        pos.x + Math.cos(angle + 0.3) * r, pos.y + 0.1, pos.z + Math.sin(angle + 0.3) * r,
                        1, 0.05, 0.05, 0.05, 0.003);
            }
        }

        // meant to be a water swirl
        int waterPoints = (int)(4 + progress * 6);
        for (int i = 0; i < waterPoints; i++) {
            double angle = -(time * 0.06) + (i * Math.PI * 2.0 / waterPoints);
            double r = 1.6;
            double y = pos.y + 0.6 + Math.sin(time * 0.07 + i) * 0.25;
            world.sendParticles(WATER_TEAL,
                    pos.x + Math.cos(angle) * r, y, pos.z + Math.sin(angle) * r,
                    1, 0, 0.015, 0, 0.004);
            if (t % 4 == 0) {
                world.sendParticles(WATER_CYAN,
                        pos.x + Math.cos(angle) * r, y + 0.15, pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.02, 0.02, 0.006);
            }
        }

        // Air going up
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.2;
                double h     = 1.5 + world.random.nextDouble() * 2.5;
                world.sendParticles(AIR_PALE,
                        pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                        1, 0.04, 0.06, 0.04, 0.01);
            }
        }

        if (t % 30 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WATER_AMBIENT,
                    SoundSource.PLAYERS, 0.3f, 0.9f + (float) progress * 0.2f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.6f, 0.9f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos       = sp.position();
        long  time      = world.getGameTime();

        double columnRadius = 3.5;

        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle        = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx           = pos.x + Math.cos(angle) * columnRadius;
            double cz           = pos.z + Math.sin(angle) * columnRadius;
            double columnHeight = COLUMN_HEIGHT * progress;
            int    colSteps     = (int)(columnHeight / 0.5) + 1;

            for (int s = 0; s < colSteps; s++) {
                double y = pos.y + s * 0.5;
                if (t % 3 == 0) {
                    world.sendParticles(COL_PRIMARY[col],
                            cx + (world.random.nextDouble() - 0.5) * 0.4, y,
                            cz + (world.random.nextDouble() - 0.5) * 0.4,
                            1, 0.04, 0.03, 0.04, 0.008);
                }
                if (t % 5 == 0 && s % 2 == 0) {
                    world.sendParticles(COL_SECONDARY[col], cx, y + 0.15, cz,
                            1, 0.06, 0.02, 0.06, 0.006);
                }
            }

            // Top
            if (columnHeight > 2.0) {
                world.sendParticles(COL_PRIMARY[col],
                        cx, pos.y + columnHeight, cz, 2, 0.3, 0.2, 0.3, 0.02);
            }
        }

        // Air swirl
        if (t % 4 == 0) {
            double apexY = pos.y + COLUMN_HEIGHT * progress;
            for (int i = 0; i < 5; i++) {
                double a = (time * 0.12) + (i * Math.PI * 2.0 / 5);
                world.sendParticles(AIR_PALE,
                        pos.x + Math.cos(a) * (columnRadius * 0.6), apexY,
                        pos.z + Math.sin(a) * (columnRadius * 0.6),
                        1, 0.05, 0.04, 0.05, 0.01);
            }
        }

        // different sounds
        if (t == 15) world.playSound(null, sp.blockPosition(),
                SoundEvents.BLAZE_AMBIENT,       SoundSource.PLAYERS, 0.4f, 1.1f);
        if (t == 30) world.playSound(null, sp.blockPosition(),
                SoundEvents.POWDER_SNOW_FALL,     SoundSource.PLAYERS, 0.5f, 0.8f);
        if (t == 45) world.playSound(null, sp.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.9f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3  pos      = sp.position();

        // shrink radius to player
        double surgeRadius = 3.5 - progress * 2.5;

        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx    = pos.x + Math.cos(angle) * surgeRadius;
            double cz    = pos.z + Math.sin(angle) * surgeRadius;

            // make it a lil random
            int colSteps = (int)(COLUMN_HEIGHT * (0.6 + progress * 0.4));
            for (int s = 0; s < colSteps; s++) {
                if (t % 2 == 0) {
                    world.sendParticles(COL_PRIMARY[col],
                            cx + (world.random.nextDouble() - 0.5) * (0.4 + progress * 0.6),
                            pos.y + s * 0.45,
                            cz + (world.random.nextDouble() - 0.5) * (0.4 + progress * 0.6),
                            1, 0.06, 0.04, 0.06, 0.012);
                }
            }

            // stuff towards player
            if (t % 5 == 0) {
                Vec3 toPlayer = pos.add(0, 1, 0)
                        .subtract(cx, pos.y + COLUMN_HEIGHT * 0.5, cz)
                        .normalize().scale(0.08 + progress * 0.06);
                world.sendParticles(COL_SECONDARY[col],
                        cx, pos.y + COLUMN_HEIGHT * 0.5, cz,
                        1, toPlayer.x, toPlayer.y + 0.02, toPlayer.z, 0.01);
            }
        }

        // try and blend them
        long time = world.getGameTime();
        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double a = (time * 0.1) + (col * Math.PI * 2.0 / COL_PRIMARY.length);
            double r = 1.4 - progress * 0.5;
            world.sendParticles(COL_PRIMARY[col],
                    pos.x + Math.cos(a) * r, pos.y + 1.0, pos.z + Math.sin(a) * r,
                    1, 0.03, 0.03, 0.03, 0.01);
        }

        // ramp it up
        if (t % 18 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BLAZE_AMBIENT,    SoundSource.PLAYERS,
                    (float)(0.3 + progress * 0.4), 0.8f + (float) progress * 0.3f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WATER_AMBIENT,     SoundSource.PLAYERS,
                    (float)(0.3 + progress * 0.3), 1.0f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,   SoundSource.PLAYERS, 1.3f, 0.6f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.0f);

            Vec3 pos = sp.position();
            for (DustParticleOptions col : COL_PRIMARY) {
                world.sendParticles(col, pos.x, pos.y + 1.0, pos.z,
                        12, 1.2, 1.0, 1.2, 0.1);
            }
            world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.2, 0.2, 0.2, 0);
        }

        Vec3  pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;

        // blindess
        if (progress > 0.15f && progress < 0.88f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 25, 0, true, false, false));
        }

        // damage no kill
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                // Apply Custom Ritual Damage Type
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // engulf target
        int burstCount = (int)(8 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            DustParticleOptions col = COL_PRIMARY[i % COL_PRIMARY.length];
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 1.8;
            double h     = world.random.nextDouble() * 2.5;
            world.sendParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                    1, 0, 0.03, 0, 0.018);
        }

        // rainbow mess
        if (t % 4 == 0) {
            // Fire
            world.sendParticles(ParticleTypes.FLAME,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0.08, 0.08, 0.08, 0.025);
            // Ice
            world.sendParticles(ParticleTypes.SNOWFLAKE,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.0,
                    pos.y + 1.0 + world.random.nextDouble() * 1.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0.04, 0.04, 0.04, 0.015);
        }
        if (t % 5 == 0) {
            // Lightning
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.2,
                    pos.y + 0.5 + world.random.nextDouble() * 1.8,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.2,
                    1, 0.06, 0.1, 0.06, 0.03);
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSLOOP.get(),
                    SoundSource.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f);
        }

        // Calm it down
        if (progress > 0.8f) {
            for (DustParticleOptions sec : COL_SECONDARY) {
                world.sendParticles(sec, pos.x, pos.y + 1.2, pos.z,
                        1, 0.4, 0.3, 0.4, 0.006);
            }
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        // Pick randomly from the elemental power pool
        Supplier<PowerInterface> factory =
                ELEMENTAL_POWERS.get(sp.getRandom().nextInt(ELEMENTAL_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.clearFire();
        sp.heal(3.0f);

        Vec3 pos = sp.position();
        for (DustParticleOptions col : COL_PRIMARY) {
            world.sendParticles(col, pos.x, pos.y + 1.0, pos.z,
                    10, 1.0, 0.8, 1.0, 0.09);
        }
        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}