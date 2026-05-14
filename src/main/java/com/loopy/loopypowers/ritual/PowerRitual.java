package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PowerManager;
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

public class PowerRitual implements RitualInterface {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // timer
    private static final int STAGE_1_TICKS = 80;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 80;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    // final stage
    private static final float STAGE_4_DAMAGE_PER_TICK = 0.3f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    // the above glowy thing
    private static final double SHINE_START_HEIGHT = 12.0;

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleOptions RITUAL_PINK =
            new DustParticleOptions(new Vector3f(1.0f, 0.4f, 0.8f), 1.3f); // pink
    private static final DustParticleOptions RITUAL_WHITE_PINK =
            new DustParticleOptions(new Vector3f(1.0f, 0.7f, 0.9f), 1.1f); // pinkish white
    private static final DustParticleOptions RITUAL_WHITE =
            new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.2f); // white
    private static final DustParticleOptions RITUAL_PALE =
            new DustParticleOptions(new Vector3f(0.9f, 0.85f, 1.0f), 0.9f); // more purpely

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PowerRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    /** return true when done */
    public boolean tick(ServerLevel world) {
        // if they die/disconnect mid-ritual, clean up and cancel it
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayer sp) {
                cancelRitual(sp);
            }
            return true;
        }

        if (!(player instanceof ServerPlayer sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        // ambient loop plays evenly throughout the entire ritual
        if (ticks == 1 || ticks % 28 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALLOOP.get(), SoundSource.PLAYERS, 0.5f, 0.7f);
        }

        int stage = currentStage();
        int stageTick = stageLocalTick();

        // Lock position
        if (stage < 4) {
            lockPosition(sp);
        }

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

        // End
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

    /** Ticks elapsed within the current stage. */
    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    /** stops all movement. */
    private void lockPosition(ServerPlayer sp) {
        // Stop all motion
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;
        sp.fallDistance = 0;

        // stop movement
        sp.setOnGround(true);

        sp.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 5, 10, true, false, false));

        // intentionally removed resistance so players can be killed during it
    }

    /** cleans up if the ritual is interrupted by death or disconnect */
    private void cancelRitual(ServerPlayer sp) {
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        // On entry
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_CLUSTER_PLACE,
                    SoundSource.PLAYERS, 0.9f, 0.7f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos = sp.position();
        long time = world.getGameTime();

        // Orbit radius expand
        double radius = 1.2 + progress * 0.8;
        int orbitPoints = 6;

        for (int i = 0; i < orbitPoints; i++) {
            double angle = (time * 0.07) + (i * Math.PI * 2.0 / orbitPoints);
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + 0.8 + Math.sin(time * 0.08 + i) * 0.3;

            world.sendParticles(RITUAL_PINK,   x, y, z, 1, 0, 0.02, 0, 0.005);
            world.sendParticles(RITUAL_PALE,   x, y + 0.2, z, 1, 0.03, 0.03, 0.03, 0.008);
        }

        if (t % 3 == 0) {
            int circlePoints = 16;
            for (int i = 0; i < circlePoints; i++) {
                double angle = Math.PI * 2.0 * i / circlePoints;
                double x = pos.x + Math.cos(angle) * 2.0;
                double z = pos.z + Math.sin(angle) * 2.0;
                world.sendParticles(RITUAL_PINK, x, pos.y + 0.05, z, 1, 0, 0.01, 0, 0.003);
            }
        }

        // Tick sound
        if (t % 20 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_CLUSTER_PLACE,
                    SoundSource.PLAYERS, 0.4f, 1.2f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSTELEPORT2.get(),
                    SoundSource.PLAYERS, 0.5f, 1.4f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos = sp.position();
        long time = world.getGameTime();

        // keep spiral
        int spiralPoints = (int)(6 + progress * 10);
        double spiralRadius = 1.0 + progress * 0.5;
        for (int i = 0; i < spiralPoints; i++) {
            double angle = (time * 0.09) + (i * Math.PI * 2.0 / spiralPoints);
            double heightVar = (i / (double) spiralPoints) * 2.0;
            double x = pos.x + Math.cos(angle) * spiralRadius;
            double z = pos.z + Math.sin(angle) * spiralRadius;

            world.sendParticles(RITUAL_PINK,   x, pos.y + heightVar, z, 1, 0, 0.03, 0, 0.006);
            world.sendParticles(RITUAL_WHITE_PINK,  x, pos.y + heightVar + 0.15, z, 1, 0.02, 0.02, 0.02, 0.005);
        }

        // above particles
        double shineY = pos.y + SHINE_START_HEIGHT;
        int shineDensity = (int)(5 + progress * 20);
        for (int i = 0; i < shineDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.sendParticles(RITUAL_WHITE_PINK,
                    pos.x + Math.cos(angle) * r,
                    shineY + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.z + Math.sin(angle) * r,
                    1, 0.05, 0.08, 0.05, 0.02);
        }

        if (t % 3 == 0) {
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.0,
                    shineY,
                    pos.z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0, 0.05, 0, 0.03);
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 8; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 2.5;
                world.sendParticles(RITUAL_WHITE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * r,
                        1, 0, 0.04, 0, 0.015);
            }
        }
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.PLAYERS, 0.7f, 0.5f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3 pos = sp.position();
        long time = world.getGameTime();

        // start lowering the clump
        double playerHeadY = pos.y + 2.5;
        double shineY = pos.y + SHINE_START_HEIGHT - (SHINE_START_HEIGHT - 2.5) * progress;

        // make it denser
        int shineDensity = 20 + (int)(progress * 15);
        double shineRadius = 1.5 - progress * 0.8; // shrinks

        for (int i = 0; i < shineDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * shineRadius;

            world.sendParticles(RITUAL_WHITE_PINK,
                    pos.x + Math.cos(angle) * r,
                    shineY + (world.random.nextDouble() - 0.3) * 1.2,
                    pos.z + Math.sin(angle) * r,
                    1, 0.04, 0.06, 0.04, 0.015 + progress * 0.02);
        }

        // Beam of particles connecting to player
        if (t % 2 == 0) {
            double beamSegments = 8;
            for (int i = 0; i < beamSegments; i++) {
                double beamT = i / beamSegments;
                double beamY = pos.y + 1.0 + (shineY - pos.y - 1.0) * beamT;
                world.sendParticles(RITUAL_PINK,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.2,
                        beamY,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.2,
                        1, 0, 0.03, 0, 0.01);
            }
        }

        // when its close
        if (progress > 0.7f && t % 3 == 0) {
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.5,
                    shineY,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.5,
                    2, 0.1, 0.1, 0.1, 0.05);
        }

        // keep spiral
        for (int i = 0; i < 8; i++) {
            double angle = (time * 0.12) + (i * Math.PI * 2.0 / 8);
            world.sendParticles(RITUAL_WHITE,
                    pos.x + Math.cos(angle) * 1.2,
                    pos.y + 1.0,
                    pos.z + Math.sin(angle) * 1.2,
                    1, 0, 0.02, 0, 0.005);
        }

        // contact
        if (progress > 0.95f) {
            world.sendParticles(RITUAL_WHITE_PINK, pos.x, pos.y + 1.5, pos.z,
                    10, 0.5, 0.5, 0.5, 0.06);
            world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.5, pos.z,
                    1, 0, 0, 0, 0);

            if (t % 5 == 0) {
                world.playSound(null, sp.blockPosition(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.PLAYERS, 0.5f, 1.8f);
            }
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            // impact stuff
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 1.2f, 0.6f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.PLAYERS, 0.8f, 1.0f);

            Vec3 pos = sp.position();
            world.sendParticles(RITUAL_WHITE_PINK, pos.x, pos.y + 1.0, pos.z,
                    40, 1.2, 1.0, 1.2, 0.1);
            world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.3, 0.3, 0.3, 0);

        }

        Vec3 pos = sp.position();

        // Sustained blindness
        sp.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, 25, 0, true, false, false));

        // damage without kill
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float currentHealth = sp.getHealth();
            if (currentHealth > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // fx around player
        double progress = (double) t / STAGE_4_TICKS;
        int burstCount = (int)(5 + (1.0 - progress) * 10); // more chaotic at start settles at end

        for (int i = 0; i < burstCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.8;
            double h = world.random.nextDouble() * 2.5;

            world.sendParticles(world.random.nextFloat() < 0.5f ? RITUAL_PINK : RITUAL_WHITE_PINK,
                    pos.x + Math.cos(angle) * r,
                    pos.y + h,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0.03, 0, 0.02);
        }

        // occasional other stuff
        if (t % 8 == 0) {
            world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z,
                    3, 0.4, 0.3, 0.4, 0.06);
        }

        if (t % 5 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSLOOP.get(),
                    SoundSource.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f);
        }

        // calm it down
        if (progress > 0.75f) {
            world.sendParticles(RITUAL_PALE,
                    pos.x, pos.y + 1.0, pos.z,
                    4, 0.5, 0.4, 0.5, 0.01);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        // Grant the power
        PowerManager.assignRandomPower(sp);

        // remove effects
        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.heal(2.0f);

        // final particles
        Vec3 pos = sp.position();
        world.sendParticles(RITUAL_PINK,  pos.x, pos.y + 1.0, pos.z, 30, 1.0, 0.8, 1.0, 0.08);
        world.sendParticles(RITUAL_WHITE_PINK, pos.x, pos.y + 1.0, pos.z, 20, 0.8, 0.6, 0.8, 0.06);
        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}