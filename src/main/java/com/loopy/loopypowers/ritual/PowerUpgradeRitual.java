package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
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
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.joml.Vector3f;

public class PowerUpgradeRitual implements RitualInterface {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 50;
    private static final int STAGE_2_TICKS = 45;
    private static final int STAGE_3_TICKS = 55;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS;

    private static final float STAGE_2_DAMAGE_PER_TICK = 0.3f;
    private static final int   STAGE_2_DAMAGE_INTERVAL = 12;
    private static final float STAGE_2_MIN_HEALTH      = 0.5f;

    /* ============================================================
       CAMERA SHAKE
       ============================================================ */

    private static final int   SHAKE_RADIUS            = 6;
    private static final int   SHAKE_STAGE_1_INTERVAL  = 20;
    private static final int   SHAKE_STAGE_2_INTERVAL  = 12;
    private static final int   SHAKE_STAGE_3_INTERVAL  = 6;
    private static final float SHAKE_STAGE_1_INTENSITY = 0.08f;
    private static final float SHAKE_STAGE_2_INTENSITY = 0.15f;
    private static final float SHAKE_STAGE_3_BURST     = 0.35f;
    private static final float SHAKE_STAGE_3_BASE      = 0.22f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleOptions MAGENTA =
            new DustParticleOptions(new Vector3f(0.95f, 0.15f, 0.70f), 1.4f);
    private static final DustParticleOptions PURPLE =
            new DustParticleOptions(new Vector3f(0.52f, 0.08f, 0.80f), 1.3f);
    private static final DustParticleOptions PINK =
            new DustParticleOptions(new Vector3f(1.00f, 0.40f, 0.80f), 1.2f);
    private static final DustParticleOptions VIOLET =
            new DustParticleOptions(new Vector3f(0.75f, 0.35f, 0.95f), 1.1f);
    private static final DustParticleOptions WHITE =
            new DustParticleOptions(new Vector3f(1.00f, 0.95f, 1.00f), 1.5f);

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PowerUpgradeRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    @Override
    public boolean tick(ServerLevel world) {
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayer sp) {
                cancelRitual(sp);
            }
            return true;
        }

        if (!(player instanceof ServerPlayer sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
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
        return 3;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
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
                    SoundEvents.BEACON_AMBIENT,     SoundSource.PLAYERS, 1.0f, 0.6f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 0.4f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        if (t % SHAKE_STAGE_1_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_1_INTENSITY + (float) progress * 0.05f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 6, intensity);
        }

        // Swirling floor particles
        int spiralArms = 4;
        for (int i = 0; i < spiralArms; i++) {
            double angle = (time * 0.08) + (Math.PI * 2.0 * i / spiralArms);
            double r     = 1.0 + progress * 2.0;
            DustParticleOptions col = (i % 2 == 0) ? MAGENTA : PURPLE;
            world.sendParticles(col,
                    pos.x + Math.cos(angle) * r,
                    pos.y + 0.05,
                    pos.z + Math.sin(angle) * r,
                    1, 0.02, 0.01, 0.02, 0.005);
        }

        // Rising ambient specs
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 2.5;
                world.sendParticles(VIOLET,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1 + world.random.nextDouble() * 2.0 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.03, 0.01, 0.008);
            }
        }

        if (t % 15 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.5f, 0.5f + (float) progress * 0.5f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE,       SoundSource.PLAYERS, 0.8f, 0.8f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        if (t % SHAKE_STAGE_2_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_2_INTENSITY + (float) progress * 0.08f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // Orbiting rings
        double ringR = 2.0 - progress * 0.5;
        if (t % 2 == 0) {
            int ringPoints = 16;
            for (int i = 0; i < ringPoints; i++) {
                double angle1 = (time * 0.1) + i * Math.PI * 2.0 / ringPoints;
                world.sendParticles(MAGENTA,
                        pos.x + Math.cos(angle1) * ringR,
                        pos.y + 0.8 + Math.sin(time * 0.05 + i) * 0.4,
                        pos.z + Math.sin(angle1) * ringR,
                        1, 0.01, 0.01, 0.01, 0.004);

                double angle2 = -(time * 0.12) + i * Math.PI * 2.0 / ringPoints;
                world.sendParticles(PURPLE,
                        pos.x + Math.cos(angle2) * (ringR * 0.7),
                        pos.y + 1.2 + Math.cos(time * 0.06 + i) * 0.3,
                        pos.z + Math.sin(angle2) * (ringR * 0.7),
                        1, 0.01, 0.01, 0.01, 0.004);
            }
        }

        // Damage ticks
        if (t % STAGE_2_DAMAGE_INTERVAL == 0) {
            if (sp.getHealth() > STAGE_2_MIN_HEALTH + STAGE_2_DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), STAGE_2_DAMAGE_PER_TICK);
            }
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 2.0;
                world.sendParticles(WHITE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * r,
                        1, 0, 0.05, 0, 0.015);
            }
        }

        if (t == 20) world.playSound(null, sp.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_PLACE,  SoundSource.PLAYERS, 0.7f, 0.6f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,      SoundSource.PLAYERS, 1.0f, 0.8f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 0.9f);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 10, SHAKE_STAGE_3_BURST);

            // Initial burst
            Vec3 pos = sp.position();
            for (int d = 0; d < 32; d++) {
                double theta = d * Math.PI * 2.0 / 32;
                double phi   = world.random.nextDouble() * Math.PI;
                double speed = 0.15 + world.random.nextDouble() * 0.15;
                DustParticleOptions col = (d % 2 == 0) ? MAGENTA : WHITE;
                world.sendParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
            world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.2, 0.2, 0.2, 0);
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_3_TICKS;
        long   time     = world.getGameTime();

        if (t % SHAKE_STAGE_3_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_3_BASE * (float)(1.0 - progress * 0.60);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 6, Math.max(intensity, 0.05f));
        }

        // Blindness briefly
        if (progress < 0.6f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // Tight rotating sphere
        double sphereRadius = 1.8 * (1.0 - progress * 0.6);
        if (t % 2 == 0) {
            int points = 16;
            for (int i = 0; i < points; i++) {
                double u = world.random.nextDouble();
                double v = world.random.nextDouble();
                double theta = 2.0 * Math.PI * u;
                double phi   = Math.acos(2.0 * v - 1.0);

                double px = pos.x + Math.sin(phi) * Math.cos(theta) * sphereRadius;
                double py = pos.y + 1.0 + Math.cos(phi) * sphereRadius;
                double pz = pos.z + Math.sin(phi) * Math.sin(theta) * sphereRadius;

                DustParticleOptions col = switch (world.random.nextInt(3)) {
                    case 0  -> WHITE;
                    case 1  -> PINK;
                    default -> VIOLET;
                };
                world.sendParticles(col, px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        // Ground absorption lines
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 2.0 + world.random.nextDouble() * 3.0;
                Vec3 from  = new Vec3(pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r);
                Vec3 dir   = pos.add(0, 1.0, 0).subtract(from).normalize().scale(0.12);

                world.sendParticles(PURPLE, from.x, from.y, from.z, 1, dir.x, dir.y, dir.z, 0.015);
            }
        }

        if (t % 6 == 0) {
            world.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y + 1.0, pos.z,
                    8, 0.5, 0.6, 0.5, 1.5);
        }

        if (t % 10 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                    0.5f, 0.8f + (float) progress * 0.8f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        PowerManager.setLevel(sp, 2);

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;

        sp.heal(4.0f);

        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.20f);

        Vec3 pos = sp.position();

        int ringPoints = 36;
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.PI * 2.0 * i / ringPoints;
            world.sendParticles(WHITE,
                    pos.x + Math.cos(angle) * 1.5,
                    pos.y + 0.1,
                    pos.z + Math.sin(angle) * 1.5,
                    2, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05,
                    0.0);
        }
        world.sendParticles(MAGENTA,  pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        world.sendParticles(PURPLE,  pos.x, pos.y + 1.0, pos.z, 14, 1.1, 1.0, 1.1, 0.08);
        world.sendParticles(WHITE,   pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        world.sendParticles(VIOLET,  pos.x, pos.y + 1.0, pos.z, 10, 0.8, 0.8, 0.8, 0.07);
        world.sendParticles(PINK,   pos.x, pos.y + 1.0, pos.z,  8, 0.7, 0.7, 0.7, 0.06);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                16, 1.0, 1.2, 1.0, 0.18);
        world.sendParticles(ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z,
                10, 0.8, 0.7, 0.8, 0);
        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.2, pos.z,
                3, 0.2, 0.2, 0.2, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, sp.blockPosition(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 1.0f, 1.1f);
        world.playSound(null, sp.blockPosition(),
                SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0f, 1.4f);

        sp.sendSystemMessage(
                Component.translatable("ritual.loopypowers.upgrade.level_2")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
        );
    }
}