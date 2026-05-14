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

public class RuinRitual implements RitualInterface {

    /* ============================================================
       POWERS
       ============================================================ */

    private static final List<Supplier<PowerInterface>> RUIN_POWERS = List.of(
            ExplosionPower::new,
            FortunePower::new,
            BloodPower::new
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

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.5f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleOptions PURPLE =
            new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.8f), 1.5f);
    private static final DustParticleOptions RED =
            new DustParticleOptions(new Vector3f(0.9f, 0.0f, 0.1f), 1.4f);
    private static final DustParticleOptions PURPLE_DARK =
            new DustParticleOptions(new Vector3f(0.2f, 0.0f, 0.3f), 1.6f);
    private static final DustParticleOptions ORANGE =
            new DustParticleOptions(new Vector3f(1.0f, 0.4f, 0.0f), 1.2f);
    private static final DustParticleOptions BLACK =
            new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.05f), 1.3f);

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public RuinRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

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

        if (ticks == 1 || ticks % 28 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.RITUALLOOP.get(), SoundSource.PLAYERS, 0.5f, 0.6f);
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
                    ModSounds.RITUALSTART.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.8f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // expanding sigil base
        double r = 1.0 + progress * 2.5;
        if (t % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double angle = (time * 0.05) + (i * Math.PI * 2.0 / 16);
                DustParticleOptions col = (i % 2 == 0) ? PURPLE : PURPLE_DARK;
                world.sendParticles(col,
                        pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r,
                        1, 0.03, 0.01, 0.03, 0.005);
            }
        }

        // sporadic flames/embers
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double rad   = world.random.nextDouble() * r;
                DustParticleOptions col = world.random.nextBoolean() ? RED : ORANGE;
                world.sendParticles(col,
                        pos.x + Math.cos(angle) * rad, pos.y + 0.1, pos.z + Math.sin(angle) * rad,
                        1, 0.02, 0.06, 0.02, 0.01);
            }
        }

        if (t % 6 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            world.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x + Math.cos(angle) * r * 0.8, pos.y + 0.2, pos.z + Math.sin(angle) * r * 0.8,
                    1, 0.0, 0.05, 0.0, 0.01);
        }

        if (t % 20 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.LAVA_POP,
                    SoundSource.PLAYERS, 0.5f, 0.7f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSTELEPORT2.get(), SoundSource.PLAYERS, 0.8f, 0.6f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 0.7f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // chaotic swirling columns
        double hMax = 4.0 + progress * 5.0;
        for (int c = 0; c < 4; c++) {
            double baseAngle = c * (Math.PI / 2.0);
            int steps = (int)(hMax / 0.5) + 1;
            for (int s = 0; s < steps; s++) {
                double y = pos.y + s * 0.5;
                double angle = baseAngle + s * 0.4 + time * 0.08;
                double r = 2.0 + Math.sin(time * 0.1 + s) * 0.5;

                if (t % 2 == 0) {
                    DustParticleOptions col = (s % 2 == 0) ? PURPLE_DARK : RED;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * r, y, pos.z + Math.sin(angle) * r,
                            1, 0.05, 0.02, 0.05, 0.01);
                }
            }
        }

        // ground cracks / dark energy
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 1.0 + world.random.nextDouble() * 3.0;
                world.sendParticles(BLACK,
                        pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r,
                        1, 0.03, 0.08, 0.03, 0.02);
            }
        }

        // fiery pops
        if (t % 5 == 0) {
            world.sendParticles(ParticleTypes.FLAME,
                    pos.x + (world.random.nextDouble() - 0.5) * 4.0,
                    pos.y + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 4.0,
                    2, 0.05, 0.05, 0.05, 0.02);
        }

        if (t == 30) world.playSound(null, sp.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.4f, 1.2f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 0.7f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.5f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // converging sphere of destruction
        double r = 4.0 - progress * 2.5;

        for (int i = 0; i < 12; i++) {
            double u = world.random.nextDouble();
            double v = world.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi   = Math.acos(2.0 * v - 1.0);

            double cx = pos.x + r * Math.sin(phi) * Math.cos(theta);
            double cy = pos.y + 1.0 + r * Math.cos(phi);
            double cz = pos.z + r * Math.sin(phi) * Math.sin(theta);

            DustParticleOptions col = switch (world.random.nextInt(4)) {
                case 0 -> PURPLE;
                case 1 -> RED;
                case 2 -> ORANGE;
                default -> BLACK;
            };

            world.sendParticles(col, cx, cy, cz, 1, 0.02, 0.02, 0.02, 0.01);
        }

        // inward pull
        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double dist  = r + 1.5;
                Vec3 from  = new Vec3(
                        pos.x + Math.cos(angle) * dist,
                        pos.y + 1.0 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * dist
                );
                Vec3 toward = pos.add(0, 1.0, 0).subtract(from).normalize().scale(0.1 + progress * 0.08);

                DustParticleOptions col = world.random.nextBoolean() ? PURPLE_DARK : RED;
                world.sendParticles(col, from.x, from.y, from.z, 1, toward.x, toward.y, toward.z, 0.02);
            }
        }

        // smoke building up
        if (t % 4 == 0) {
            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.0,
                    pos.y + 0.2 + world.random.nextDouble(),
                    pos.z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0, 0.04, 0, 0.02);
        }

        if (t % 12 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.6f, 0.5f + (float) progress * 0.5f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3f, 0.5f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);

            Vec3 pos = sp.position();
            world.sendParticles(PURPLE,       pos.x, pos.y + 1.0, pos.z, 25, 1.5, 1.2, 1.5, 0.15);
            world.sendParticles(RED,          pos.x, pos.y + 1.0, pos.z, 20, 1.3, 1.0, 1.3, 0.12);
            world.sendParticles(BLACK,        pos.x, pos.y + 1.0, pos.z, 15, 1.0, 0.8, 1.0, 0.10);
            world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 3, 0.5, 0.5, 0.5, 0);
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;

        if (progress > 0.1f && progress < 0.85f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 25, 0, true, false, false));
        }

        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // chaotic bursts
        int burstCount = (int)(12 + (1.0 - progress) * 15);
        for (int i = 0; i < burstCount; i++) {
            DustParticleOptions col = switch (world.random.nextInt(4)) {
                case 0  -> PURPLE;
                case 1  -> RED;
                case 2  -> PURPLE_DARK;
                default -> ORANGE;
            };
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 2.5;
            double h     = world.random.nextDouble() * 3.0;
            world.sendParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                    1, 0, 0.04, 0, 0.02);
        }

        // fire / smoke
        if (t % 3 == 0) {
            world.sendParticles(ParticleTypes.FLAME,
                    pos.x + (world.random.nextDouble() - 0.5) * 3.0,
                    pos.y + world.random.nextDouble() * 2.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 3.0,
                    2, 0.02, 0.06, 0.02, 0.015);
        }
        if (t % 4 == 0) {
            world.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.0,
                    pos.y + 0.5 + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0.05, 0.05, 0.05, 0.02);
        }

        if (t % 6 == 0) {
            world.playSound(null, sp.blockPosition(),
                    ModSounds.DARKNESSLOOP.get(),
                    SoundSource.PLAYERS,
                    0.4f, 0.6f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        Supplier<PowerInterface> factory =
                RUIN_POWERS.get(sp.getRandom().nextInt(RUIN_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;
        sp.clearFire();
        sp.heal(3.0f);

        Vec3 pos = sp.position();
        world.sendParticles(PURPLE,  pos.x, pos.y + 1.0, pos.z, 14, 1.2, 1.0, 1.2, 0.09);
        world.sendParticles(RED, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.08);
        world.sendParticles(PURPLE_DARK,  pos.x, pos.y + 1.0, pos.z,  8, 0.8, 0.7, 0.8, 0.06);
        world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z,
                2, 0.3, 0.2, 0.3, 0);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}