package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.PerfectedUpgradeRitualTickPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class PerfectedUpgradeRitual implements RitualInterface {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;

    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float DAMAGE_PER_TICK = 0.35f;
    private static final int   DAMAGE_INTERVAL = 10;
    private static final float MIN_HEALTH      = 0.5f;

    private static final int   SHAKE_RADIUS            = 8;
    private static final int   SHAKE_STAGE_1_INTERVAL  = 18;
    private static final int   SHAKE_STAGE_2_INTERVAL  = 10;
    private static final int   SHAKE_STAGE_3_INTERVAL  = 6;
    private static final int   SHAKE_STAGE_4_INTERVAL  = 4;
    private static final float SHAKE_STAGE_1_INTENSITY = 0.10f;
    private static final float SHAKE_STAGE_2_INTENSITY = 0.20f;
    private static final float SHAKE_STAGE_3_INTENSITY = 0.28f;
    private static final float SHAKE_STAGE_4_BURST     = 0.50f;
    private static final float SHAKE_STAGE_4_BASE      = 0.30f;

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PerfectedUpgradeRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

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

        // SYNC TICKS TO CLIENT
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new PerfectedUpgradeRitualTickPayload(sp.getId(), ticks));

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 1 -> tickStage1ServerOnly(sp, stageTick);
            case 2 -> tickStage2ServerOnly(sp, world, stageTick);
            case 3 -> tickStage3ServerOnly(sp, world, stageTick);
            case 4 -> tickStage4ServerOnly(sp, stageTick);
        }

        if (ticks >= TOTAL_TICKS) {
            onComplete(sp, world);
            return true;
        }

        return false;
    }

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
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 10, true, false, false));
    }

    private void cancelRitual(ServerPlayer sp) {
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.CONFUSION);
    }

    private void tickStage1ServerOnly(ServerPlayer sp, int t) {
        double progress = (double) t / STAGE_1_TICKS;
        if (t % SHAKE_STAGE_1_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_1_INTENSITY + (float) progress * 0.05f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }
    }

    private void tickStage2ServerOnly(ServerPlayer sp, ServerLevel world, int t) {
        double progress = (double) t / STAGE_2_TICKS;

        if (t % SHAKE_STAGE_2_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_2_INTENSITY + (float) progress * (SHAKE_STAGE_3_INTENSITY - SHAKE_STAGE_2_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        if (progress > 0.35f && progress < 0.92f) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 15, 0, true, false, false));
        }

        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), DAMAGE_PER_TICK);
            }
        }
    }

    private void tickStage3ServerOnly(ServerPlayer sp, ServerLevel world, int t) {
        if (t == 1) {
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_3_INTENSITY + 0.05f);
        }

        double progress = (double) t / STAGE_3_TICKS;

        if (t % SHAKE_STAGE_3_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_3_INTENSITY + (float) progress * (SHAKE_STAGE_4_BASE - SHAKE_STAGE_3_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 15, 0, true, false, false));
        if (progress > 0.40f) {
            sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30, 0, true, false, false));
        }

        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), DAMAGE_PER_TICK);
            }
        }
    }

    private void tickStage4ServerOnly(ServerPlayer sp, int t) {
        if (t == 1) {
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_4_BURST);
        }

        double progress = (double) t / STAGE_4_TICKS;
        int shakeInterval = (progress < 0.60) ? SHAKE_STAGE_4_INTERVAL : SHAKE_STAGE_3_INTERVAL;

        if (t % shakeInterval == 0) {
            float intensity = SHAKE_STAGE_4_BASE * (float)(1.0 - progress * 0.70);
            intensity = Math.max(intensity, SHAKE_STAGE_1_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        if (progress < 0.72f) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 15, 0, true, false, false));
        }
    }

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        PowerManager.setLevel(sp, 3);

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.CONFUSION);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;

        sp.heal(6.0f);
        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.28f);

        sp.sendSystemMessage(
                Component.translatable("ritual.loopypowers.upgrade.level_3")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
        );
    }
}