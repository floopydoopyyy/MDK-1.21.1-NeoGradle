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

public class MindRitual implements RitualInterface {

    /* ============================================================
       MIND POWER POOL
       ============================================================ */

    private static final List<Supplier<PowerInterface>> MIND_POWERS = List.of(
            PsychicPower::new,
            DarknessPower::new,
            TelekinesisPower::new
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

    /* ============================================================
       PARTICLES
       ============================================================ */

    // Hot pink
    private static final DustParticleOptions PINK_HOT =
            new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.58f), 1.4f);
    private static final DustParticleOptions PINK_PALE =
            new DustParticleOptions(new Vector3f(1.0f, 0.60f, 0.85f), 1.1f);

    // purple
    private static final DustParticleOptions PURPLE_DEEP =
            new DustParticleOptions(new Vector3f(0.45f, 0.0f, 0.70f), 1.5f);
    private static final DustParticleOptions PURPLE_SOFT =
            new DustParticleOptions(new Vector3f(0.70f, 0.30f, 1.0f),  1.2f);

    // purple black
    private static final DustParticleOptions PURPLE_BLACK =
            new DustParticleOptions(new Vector3f(0.15f, 0.0f, 0.25f), 1.6f);

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public MindRitual(Player player, RitualManager.RitualType type) {
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

        // ambient loop plays evenly throughout the entire ritual
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
        sp.removeEffect(MobEffects.DARKNESS);
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

            world.playSound(null, sp.blockPosition(),
                    SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.6f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // rings on ground
        int ringCount = 2 + (int)(progress * 2);   // grows over stages
        for (int ring = 0; ring < ringCount; ring++) {
            double phase  = (time * 0.04 + ring * 0.5) % (Math.PI * 2);
            double r      = 1.0 + ring * 0.9 + Math.sin(phase) * 0.2;
            int    points = 10 + ring * 4;
            if (t % 3 == 0) {
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    DustParticleOptions col = (ring % 2 == 0) ? PINK_HOT : PURPLE_DEEP;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * r,
                            pos.y + 0.03,
                            pos.z + Math.sin(angle) * r,
                            1, 0.04, 0.02, 0.04, 0.003);
                }
            }
        }

        // stuff rising
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.5;
                double h     = 0.5 + world.random.nextDouble() * 3.5 * progress;
                DustParticleOptions wisp = world.random.nextBoolean() ? PINK_PALE : PURPLE_SOFT;
                world.sendParticles(wisp,
                        pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.05, 0.02, 0.008);
            }
        }

        // darker stuff
        if (t % 8 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = 0.5 + world.random.nextDouble() * 0.8;
            world.sendParticles(PURPLE_BLACK,
                    pos.x + Math.cos(angle) * r, pos.y + 0.5, pos.z + Math.sin(angle) * r,
                    1, 0.01, 0.02, 0.01, 0.005);
        }

        if (t % 25 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.3f, 0.5f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.8f, 0.7f);
        }

        double progress     = (double) t / STAGE_2_TICKS;
        Vec3 pos          = sp.position();
        long   time         = world.getGameTime();
        double maxHeight    = 10.0 * progress;
        double orbitRadius  = 2.2;

        // three helix strands, meant to be DNA but not really
        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);

            int steps = (int)(maxHeight / 0.4) + 1;
            for (int s = 0; s < steps; s++) {
                double y = pos.y + s * 0.4;

                // Outer helix
                double outerAngle = baseAngle + s * 0.35 + time * 0.05;
                if (t % 2 == 0) {
                    DustParticleOptions col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    world.sendParticles(col,
                            pos.x + Math.cos(outerAngle) * orbitRadius,
                            y,
                            pos.z + Math.sin(outerAngle) * orbitRadius,
                            1, 0.05, 0.03, 0.05, 0.007);
                }

                // Inner -helix
                if (t % 3 == 0 && s % 2 == 0) {
                    double innerAngle = baseAngle - s * 0.30 - time * 0.04;
                    world.sendParticles(PURPLE_SOFT,
                            pos.x + Math.cos(innerAngle) * (orbitRadius * 0.55),
                            y + 0.1,
                            pos.z + Math.sin(innerAngle) * (orbitRadius * 0.55),
                            1, 0.03, 0.02, 0.03, 0.005);
                }
            }

            if (maxHeight > 1.5 && t % 4 == 0) {
                world.sendParticles(PINK_HOT,
                        pos.x, pos.y + maxHeight, pos.z,
                        2, 0.5, 0.15, 0.5, 0.02);
            }
        }

        // darker from the ground
        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.4 + world.random.nextDouble() * 2.5;
                world.sendParticles(PURPLE_BLACK,
                        pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        if (t == 20) world.playSound(null, sp.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT,      SoundSource.PLAYERS, 0.4f, 1.3f);
        if (t == 40) world.playSound(null, sp.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_PLACE,  SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.4f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // shrink radius, accelerate speed
        double orbitRadius = 2.2 - progress * 1.7;

        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);
            int    steps     = (int)(10.0 * (0.5 + progress * 0.5));

            for (int s = 0; s < steps; s++) {
                if (t % 2 == 0) {
                    double spin = baseAngle + s * (0.35 + progress * 0.25)
                            + time * (0.06 + progress * 0.06);
                    double y = pos.y + s * (0.4 - progress * 0.1);

                    DustParticleOptions col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    world.sendParticles(col,
                            pos.x + Math.cos(spin) * orbitRadius,
                            y,
                            pos.z + Math.sin(spin) * orbitRadius,
                            1, 0.04 + progress * 0.04, 0.03, 0.04 + progress * 0.04, 0.01);
                }
            }
        }

        // inner particles going towards player
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = orbitRadius + 1.5;
                Vec3 from  = new Vec3(
                        pos.x + Math.cos(angle) * r, pos.y + 1.0, pos.z + Math.sin(angle) * r);
                Vec3 toward = pos.add(0, 1, 0).subtract(from)
                        .normalize().scale(0.07 + progress * 0.05);
                DustParticleOptions col = world.random.nextBoolean() ? PINK_HOT : PURPLE_BLACK;
                world.sendParticles(col,
                        from.x, from.y, from.z,
                        1, toward.x, toward.y, toward.z, 0.012);
            }
        }

        // tightening ring
        if (t % 3 == 0) {
            double headRing = 0.8 - progress * 0.2;
            for (int i = 0; i < 8; i++) {
                double angle = time * 0.15 + i * Math.PI * 2.0 / 8;
                world.sendParticles(PURPLE_SOFT,
                        pos.x + Math.cos(angle) * headRing, pos.y + 1.6,
                        pos.z + Math.sin(angle) * headRing,
                        1, 0.02, 0.02, 0.02, 0.008);
            }
        }

        if (t % 15 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.ENDERMAN_AMBIENT,
                    SoundSource.PLAYERS,
                    (float)(0.3 + progress * 0.4), 1.2f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,  SoundSource.PLAYERS, 1.2f, 1.4f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.ENDERMAN_SCREAM,    SoundSource.PLAYERS, 0.8f, 0.8f);

            Vec3 pos = sp.position();
            world.sendParticles(PINK_HOT,    pos.x, pos.y + 1.0, pos.z, 20, 1.5, 1.2, 1.5, 0.12);
            world.sendParticles(PURPLE_DEEP, pos.x, pos.y + 1.0, pos.z, 15, 1.2, 1.0, 1.2, 0.10);
            world.sendParticles(PURPLE_BLACK, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.08);
            world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.3, 0.3, 0.3, 0);
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;

        // blindness
        if (progress > 0.10f && progress < 0.85f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 25, 0, true, false, false));
        }
        // Damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                // Apply Custom Ritual Damage Type
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // burst
        int burstCount = (int)(10 + (1.0 - progress) * 16);
        for (int i = 0; i < burstCount; i++) {
            DustParticleOptions col = switch (i % 3) {
                case 0  -> PINK_HOT;
                case 1  -> PURPLE_DEEP;
                default -> PURPLE_BLACK;
            };
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 2.0;
            double h     = world.random.nextDouble() * 2.8;
            world.sendParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                    1, 0, 0.02, 0, 0.018);
        }

        // emit particles
        if (t % 3 == 0) {
            world.sendParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.8,
                    pos.y + world.random.nextDouble() * 2.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.8,
                    1, 0.04, 0.08, 0.04, 0.025);
        }

        // doesnt add much
        if (t % 4 == 0) {
            world.sendParticles(ParticleTypes.WITCH,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.2,
                    pos.y + 0.5 + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.2,
                    2, 0.10, 0.10, 0.10, 0.02);
        }

        // calm it down
        if (progress > 0.78f) {
            for (int i = 0; i < 3; i++) {
                world.sendParticles(PINK_PALE,
                        pos.x, pos.y + 1.2, pos.z, 1, 0.5, 0.4, 0.5, 0.005);
                world.sendParticles(PURPLE_SOFT,
                        pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.004);
            }
        }

        if (t % 5 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_CLUSTER_PLACE,
                    SoundSource.PLAYERS, 0.25f + (float) progress * 0.15f, 1.8f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        Supplier<PowerInterface> factory =
                MIND_POWERS.get(sp.getRandom().nextInt(MIND_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.DARKNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.clearFire();
        sp.heal(3.0f);

        Vec3 pos = sp.position();
        world.sendParticles(PINK_HOT,    pos.x, pos.y + 1.0, pos.z, 14, 1.2, 1.0, 1.2, 0.09);
        world.sendParticles(PURPLE_DEEP, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.07);
        world.sendParticles(PINK_PALE,   pos.x, pos.y + 1.0, pos.z,  8, 0.8, 0.7, 0.8, 0.05);
        world.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.2f);
    }
}