package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.manager.PowerManager;
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

public class SeveranceRitual implements RitualInterface {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 65;
    private static final int STAGE_2_TICKS = 55;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 75;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.5f;   // slightly harsher than vestiges
    private static final int   STAGE_4_DAMAGE_INTERVAL = 8;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // black
    private static final DustParticleOptions BLACK =
            new DustParticleOptions(new Vector3f(0.06f, 0.01f, 0.10f), 1.6f);
    // dark purple
    private static final DustParticleOptions PURPLE =
            new DustParticleOptions(new Vector3f(0.35f, 0.00f, 0.52f), 1.5f);
    // violet
    private static final DustParticleOptions VIOLET =
            new DustParticleOptions(new Vector3f(0.60f, 0.18f, 0.82f), 1.3f);
    // red
    private static final DustParticleOptions RED =
            new DustParticleOptions(new Vector3f(0.28f, 0.01f, 0.03f), 1.5f);
    // gray
    private static final DustParticleOptions GRAY =
            new DustParticleOptions(new Vector3f(0.72f, 0.70f, 0.68f), 0.9f);

    /* ============================================================
       same lines as ruin ritual
       ============================================================ */

    private static final double[] TENDRIL_ANGLES = {
            0.0,
            Math.PI * 0.42,
            Math.PI * 0.81,
            Math.PI,
            Math.PI * 1.35,
            Math.PI * 1.78
    };

    /* ============================================================
       STATE
       ============================================================ */

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public SeveranceRitual(Player player, RitualManager.RitualType type) {
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

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

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
        sp.removeEffect(MobEffects.WEAKNESS);
        sp.removeEffect(MobEffects.CONFUSION);
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.7f, 0.4f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.GRAVEL_PLACE,           SoundSource.PLAYERS, 0.5f, 0.5f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3 pos      = sp.position();

        // 6 tendrils at start that move toward player
        double maxReach  = 5.0;
        double innerEdge = maxReach * (1.0 - progress);  // the inner end crawls to 0 over the stage

        if (t % 2 == 0) {
            for (double angle : TENDRIL_ANGLES) {
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    // particles from outer to inner edge
                    double d = innerEdge + (maxReach - innerEdge) * ((double) s / steps);

                    // slight jitter
                    double jitter = (world.random.nextDouble() - 0.5) * 0.22;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;

                    // end
                    DustParticleOptions col = (s < 4) ? BLACK
                            : (s < 7) ? PURPLE
                            : VIOLET;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.02, 0.01, 0.02, 0.002);
                }

                // tip
                if (t % 4 == 0 && innerEdge > 0.4) {
                    world.sendParticles(VIOLET,
                            pos.x + Math.cos(angle) * innerEdge,
                            pos.y + 0.12,
                            pos.z + Math.sin(angle) * innerEdge,
                            2, 0.05, 0.08, 0.05, 0.018);
                    world.sendParticles(PURPLE,
                            pos.x + Math.cos(angle) * innerEdge,
                            pos.y + 0.1,
                            pos.z + Math.sin(angle) * innerEdge,
                            1, 0.02, 0.05, 0.02, 0.010);
                }
            }
        }

        // ash
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.8 + world.random.nextDouble() * 4.0;
                world.sendParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 2.0 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.008, 0.02, 0.003);
            }
        }

        // particles from below player
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 0.6;
                world.sendParticles(BLACK,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05 + world.random.nextDouble() * 1.5 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.03, 0.01, 0.005);
            }
        }

        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 1.0 + world.random.nextDouble() * 2.5;
                world.sendParticles(PURPLE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.04, 0.01, 0.008);
            }
        }

        // idk
        if (t % 8 == 0 && progress > 0.3f) {
            double angle = TENDRIL_ANGLES[world.random.nextInt(TENDRIL_ANGLES.length)];
            double r     = innerEdge + world.random.nextDouble() * 2.0;
            world.sendParticles(ParticleTypes.WITCH,
                    pos.x + Math.cos(angle) * r,
                    pos.y + 0.5 + world.random.nextDouble() * 0.8,
                    pos.z + Math.sin(angle) * r,
                    2, 0.06, 0.06, 0.06, 0.02);
        }

        if (t % 22 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.SCULK_CLICKING,
                    SoundSource.PLAYERS, 0.5f, 0.35f + (float) progress * 0.2f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.5f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.SOUL_SAND_HIT,   SoundSource.PLAYERS, 0.8f, 0.6f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // rotate the tendrils
        double coilRotation = time * 0.03;
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                int    steps = 10;
                // coiling
                for (int s = 0; s < steps; s++) {
                    double d      = 0.6 + (double) s / steps * 3.9;
                    double jitter = (world.random.nextDouble() - 0.5) * 0.18;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;

                    DustParticleOptions col = (s < 3) ? BLACK
                            : (s < 6) ? PURPLE
                            : VIOLET;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.02, 0.01, 0.02, 0.003);
                }

                // try to move up the player - doesn't really look like that
                double riseHeight = 0.5 + progress * 3.5;
                if (t % 3 == 0) {
                    world.sendParticles(PURPLE,
                            pos.x + Math.cos(angle) * 0.7,
                            pos.y + world.random.nextDouble() * riseHeight,
                            pos.z + Math.sin(angle) * 0.7,
                            1, 0.02, 0.04, 0.02, 0.008);
                    // Brighter violet at the top of the rising column
                    if (world.random.nextFloat() < 0.4f) {
                        world.sendParticles(VIOLET,
                                pos.x + Math.cos(angle) * 0.55,
                                pos.y + riseHeight,
                                pos.z + Math.sin(angle) * 0.55,
                                1, 0.03, 0.03, 0.03, 0.012);
                    }
                }
            }
        }

        // stuff from player
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.2 + world.random.nextDouble() * 0.9;
                world.sendParticles(ParticleTypes.WARPED_SPORE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.5 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.05, 0.01, 0.012);
            }
        }

        // soul fire
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 0.8;
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.3 + world.random.nextDouble() * 2.2,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        // purple at lower half
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.4 + world.random.nextDouble() * 1.6;
                world.sendParticles(PURPLE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.3 + world.random.nextDouble() * 0.8,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.02, 0.01, 0.005);
            }
        }

        // ash
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 5.0;
                world.sendParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 1.5 + world.random.nextDouble() * 3.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.01, 0.01, 0.003);
            }
        }

        // witch
        if (t % 5 == 0) {
            world.sendParticles(ParticleTypes.WITCH,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.6,
                    pos.y + 0.5 + world.random.nextDouble() * 1.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.6,
                    3, 0.08, 0.08, 0.08, 0.025);
        }


        // Weakness creeping in — the power is being destabilised
        if (progress > 0.4f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 20, 0, true, false, false));
        }

        if (t == 20) world.playSound(null, sp.blockPosition(),
                SoundEvents.WITHER_SHOOT,          SoundSource.PLAYERS, 0.4f, 0.6f);
        if (t == 42) world.playSound(null, sp.blockPosition(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK,  SoundSource.PLAYERS, 0.5f, 0.55f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.7f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.CHAIN_BREAK,        SoundSource.PLAYERS, 0.9f, 0.7f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3 pos      = sp.position();
        long   time     = world.getGameTime();

        // explosion
        if (t % 2 == 0) {
            int burstCount = (int)(8 + progress * 14);
            for (int i = 0; i < burstCount; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI * 0.6;   // mostly horizontal/upward
                double speed = 0.08 + progress * 0.07;

                DustParticleOptions col = switch (i % 5) {
                    case 0  -> BLACK;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    case 3  -> RED;
                    default -> GRAY;
                };
                world.sendParticles(col,
                        pos.x, pos.y + 1.1, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.6 + 0.03,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // tighten coil
        double coilRotation = time * 0.05;
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                // Coil radius tightens as the power is pulled out
                double r = 0.65 * (1.0 - progress * 0.35);
                if (r > 0.2) {
                    DustParticleOptions col = (t % 4 == 0) ? VIOLET : BLACK;
                    world.sendParticles(col,
                            pos.x + Math.cos(angle) * r,
                            pos.y + 0.3,
                            pos.z + Math.sin(angle) * r,
                            1, 0.02, 0.02, 0.02, 0.006);
                }
            }
        }

        // red at feet
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.8;
                world.sendParticles(RED,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.01, 0.01, 0.003);
            }
        }

        // more soul fire
        if (t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.0;
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 2.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.05, 0.02, 0.016);
            }
        }

        // purple halo
        if (t % 3 == 0) {
            int    coronaPoints = 12;
            double coronaR      = 0.9 - progress * 0.2;
            for (int i = 0; i < coronaPoints; i++) {
                double angle = world.getGameTime() * 0.08 + i * Math.PI * 2.0 / coronaPoints;
                world.sendParticles(VIOLET,
                        pos.x + Math.cos(angle) * coronaR,
                        pos.y + 1.1,
                        pos.z + Math.sin(angle) * coronaR,
                        1, 0.02, 0.03, 0.02, 0.007);
            }
        }

        if (t % 3 == 0) {
            world.sendParticles(ParticleTypes.WITCH,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.4,
                    pos.y + 0.5 + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.4,
                    4, 0.10, 0.10, 0.10, 0.03);
        }

        // nausea -> CONFUSION
        sp.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, 30, 0, true, false, false));

        if (t % 14 == 0) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WITHER_AMBIENT,
                    SoundSource.PLAYERS,
                    (float)(0.5 + progress * 0.4), 0.55f + (float) progress * 0.35f);
        }
        if (t == 28) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM,  SoundSource.PLAYERS, 1.1f, 0.5f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.CHAIN_BREAK,         SoundSource.PLAYERS, 1.0f, 0.5f);
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.8f, 0.7f);

            Vec3 pos = sp.position();
            for (int d = 0; d < 40; d++) {
                double theta = d * Math.PI * 2.0 / 40;
                double phi   = Math.PI * 0.35 + world.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.22 + world.random.nextDouble() * 0.14;
                DustParticleOptions col = switch (d % 4) {
                    case 0  -> PURPLE;
                    case 1  -> BLACK;
                    case 2  -> RED;
                    default -> VIOLET;
                };
                world.sendParticles(col, pos.x, pos.y + 1.1, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }

            // more soul fire
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1.0, pos.z, 24, 1.1, 0.9, 1.1, 0.14);

            world.sendParticles(ParticleTypes.WITCH,
                    pos.x, pos.y + 1.0, pos.z, 16, 0.8, 0.6, 0.8, 0.10);

            world.sendParticles(ParticleTypes.ASH,
                    pos.x, pos.y + 1.0, pos.z, 35, 1.3, 0.9, 1.3, 0.11);

            // shockwave
            int ringPoints = 18;
            for (int i = 0; i < ringPoints; i++) {
                double angle = i * Math.PI * 2.0 / ringPoints;
                double speed = 0.28;
                world.sendParticles(PURPLE,
                        pos.x + Math.cos(angle) * 0.4, pos.y + 0.2, pos.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
            }
        }

        Vec3 pos      = sp.position();
        double progress = (double) t / STAGE_4_TICKS;

        // blindness
        if (progress > 0.10f && progress < 0.80f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 25, 0, true, false, false));
        }

        // darkness
        if (progress > 0.05f && progress < 0.70f) {
            sp.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS, 25, 0, true, false, false));
        }

        // weakness
        sp.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 20, 1, true, false, false));

        // damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // implosion
        if (t > 8 && t % 2 == 0) {
            int pullCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < pullCount; i++) {
                double theta  = world.random.nextDouble() * Math.PI * 2;
                double phi    = world.random.nextDouble() * Math.PI;
                double srcR   = 2.5 + world.random.nextDouble() * 4.5;
                double fromX  = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = pos.y + 1.0 + Math.cos(phi) * srcR * 0.4;
                double fromZ  = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3 toward = pos.add(0, 1, 0)
                        .subtract(fromX, fromY, fromZ)
                        .normalize()
                        .scale(0.10 + (1.0 - progress) * 0.06);

                DustParticleOptions col = switch (i % 4) {
                    case 0  -> BLACK;
                    case 1  -> GRAY;
                    case 2  -> PURPLE;
                    default -> RED;
                };
                world.sendParticles(col, fromX, fromY, fromZ,
                        1, toward.x, toward.y, toward.z, 0.008);
            }
        }

        // calm soul fire
        if (progress < 0.65f && t % 3 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 0.7;
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x + Math.cos(angle) * r,
                    pos.y + 0.2 + world.random.nextDouble() * 2.0,
                    pos.z + Math.sin(angle) * r,
                    1, 0.01, 0.03, 0.01, 0.008);
        }

        // purple
        if (progress > 0.15f && progress < 0.65f && t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 1.2;
                world.sendParticles(PURPLE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 2.2,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.02, 0.01, 0.006);
            }
        }

        // less ash
        if (t % 3 == 0 && progress < 0.75f) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 4.5;
                world.sendParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 3.0 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.015, 0.01, 0.003);
            }
        }

        if (progress > 0.72f) {
            world.sendParticles(BLACK,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.003);
            world.sendParticles(GRAY,
                    pos.x, pos.y + 0.8, pos.z, 1, 0.3, 0.2, 0.3, 0.002);
            world.sendParticles(PURPLE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.3, 0.3, 0.3, 0.002);
        }

        if (t % 8 == 0 && progress < 0.60f) {
            world.playSound(null, sp.blockPosition(),
                    SoundEvents.WITHER_AMBIENT,
                    SoundSource.PLAYERS,
                    (float)(0.4 + (1.0 - progress) * 0.3), 0.45f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        // remove the power
        PowerManager.removePower(sp);

        // Clean up status effects
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.DARKNESS);
        sp.removeEffect(MobEffects.CONFUSION); // NAUSEA -> CONFUSION
        sp.removeEffect(MobEffects.WITHER);

        // weakness cus why not
        sp.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 200, 0, false, true, true));

        Vec3 pos = sp.position();
        world.sendParticles(BLACK,     pos.x, pos.y + 1.0, pos.z, 20, 1.3, 1.0, 1.3, 0.07);
        world.sendParticles(PURPLE, pos.x, pos.y + 1.0, pos.z, 16, 1.1, 0.9, 1.1, 0.06);
        world.sendParticles(VIOLET,   pos.x, pos.y + 1.0, pos.z, 10, 0.9, 0.8, 0.9, 0.05);
        world.sendParticles(GRAY,      pos.x, pos.y + 1.0, pos.z, 12, 1.0, 0.8, 1.0, 0.05);
        world.sendParticles(ParticleTypes.ASH, pos.x, pos.y + 1.0, pos.z,
                22, 1.5, 1.0, 1.5, 0.06);
        world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z,
                8, 0.6, 0.5, 0.6, 0.05);
        world.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z,
                6, 0.5, 0.4, 0.5, 0.06);

        world.playSound(null, sp.blockPosition(),
                SoundEvents.WITHER_DEATH,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        world.playSound(null, sp.blockPosition(),
                SoundEvents.SOUL_SAND_HIT,
                SoundSource.PLAYERS, 0.6f, 0.5f);

        sp.displayClientMessage(
                Component.translatable("ritual.loopypowers.severance.complete")
                        .withStyle(ChatFormatting.RED),
                true
        );
    }
}