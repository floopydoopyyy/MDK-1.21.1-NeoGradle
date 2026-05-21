package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.CameraShake;
import com.loopy.loopypowers.network.payload.PowerUpgradeRitualTickPayload;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.network.PacketDistributor;

public class PowerUpgradeRitual implements RitualInterface {

    private static final int STAGE_1_TICKS = 50;
    private static final int STAGE_2_TICKS = 45;
    private static final int STAGE_3_TICKS = 55;

    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS;

    private static final float STAGE_2_DAMAGE_PER_TICK = 0.3f;
    private static final int   STAGE_2_DAMAGE_INTERVAL = 12;
    private static final float STAGE_2_MIN_HEALTH      = 0.5f;

    private static final int   SHAKE_RADIUS            = 6;
    private static final int   SHAKE_STAGE_1_INTERVAL  = 20;
    private static final int   SHAKE_STAGE_2_INTERVAL  = 12;
    private static final int   SHAKE_STAGE_3_INTERVAL  = 6;
    private static final float SHAKE_STAGE_1_INTENSITY = 0.08f;
    private static final float SHAKE_STAGE_2_INTENSITY = 0.15f;
    private static final float SHAKE_STAGE_3_BURST     = 0.35f;
    private static final float SHAKE_STAGE_3_BASE      = 0.22f;

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PowerUpgradeRitual(Player player, RitualManager.RitualType type) {
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
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new PowerUpgradeRitualTickPayload(sp.getId(), ticks));

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 1 -> tickStage1ServerOnly(sp, stageTick);
            case 2 -> tickStage2ServerOnly(sp, world, stageTick);
            case 3 -> tickStage3ServerOnly(sp, stageTick);
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
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 10, true, false, false));
    }

    private void cancelRitual(ServerPlayer sp) {
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
    }

    private void tickStage1ServerOnly(ServerPlayer sp, int t) {
        double progress = (double) t / STAGE_1_TICKS;
        if (t % SHAKE_STAGE_1_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_1_INTENSITY + (float) progress * 0.05f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 6, intensity);
        }
    }

    private void tickStage2ServerOnly(ServerPlayer sp, ServerLevel world, int t) {
        double progress = (double) t / STAGE_2_TICKS;

        if (t % SHAKE_STAGE_2_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_2_INTENSITY + (float) progress * 0.08f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        if (t % STAGE_2_DAMAGE_INTERVAL == 0) {
            if (sp.getHealth() > STAGE_2_MIN_HEALTH + STAGE_2_DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), STAGE_2_DAMAGE_PER_TICK);
            }
        }
    }

    private void tickStage3ServerOnly(ServerPlayer sp, int t) {
        if (t == 1) {
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 10, SHAKE_STAGE_3_BURST);
        }

        double progress = (double) t / STAGE_3_TICKS;

        if (t % SHAKE_STAGE_3_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_3_BASE * (float)(1.0 - progress * 0.60);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 6, Math.max(intensity, 0.05f));
        }

        if (progress < 0.6f) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 15, 0, true, false, false));
        }
    }

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        PowerManager.setLevel(sp, 2);

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hasImpulse = true;

        sp.heal(4.0f);
        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.20f);

        sp.sendSystemMessage(
                Component.translatable("ritual.loopypowers.upgrade.level_2")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
        );
    }
}