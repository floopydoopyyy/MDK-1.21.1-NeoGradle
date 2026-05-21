package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.payload.SeveranceRitualTickPayload;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.network.PacketDistributor;

public class SeveranceRitual implements RitualInterface {

    private static final int STAGE_1_TICKS = 65;
    private static final int STAGE_2_TICKS = 55;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 75;

    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.5f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 8;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public SeveranceRitual(Player player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

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
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new SeveranceRitualTickPayload(sp.getId(), ticks));

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 2 -> tickStage2ServerOnly(sp, stageTick);
            case 3 -> tickStage3ServerOnly(sp, stageTick);
            case 4 -> tickStage4ServerOnly(sp, world, stageTick);
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
        sp.removeEffect(MobEffects.DARKNESS);
        sp.removeEffect(MobEffects.WEAKNESS);
        sp.removeEffect(MobEffects.CONFUSION);
    }

    private void tickStage2ServerOnly(ServerPlayer sp, int t) {
        double progress = (double) t / STAGE_2_TICKS;
        if (progress > 0.4f) {
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 0, true, false, false));
        }
    }

    private void tickStage3ServerOnly(ServerPlayer sp, int t) {
        sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30, 0, true, false, false));
    }

    private void tickStage4ServerOnly(ServerPlayer sp, ServerLevel world, int t) {
        double progress = (double) t / STAGE_4_TICKS;

        if (progress > 0.10f && progress < 0.80f) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, true, false, false));
        }
        if (progress > 0.05f && progress < 0.70f) {
            sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 25, 0, true, false, false));
        }

        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1, true, false, false));

        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.hurt(world.damageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }
    }

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        PowerManager.removePower(sp);

        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.DARKNESS);
        sp.removeEffect(MobEffects.CONFUSION);
        sp.removeEffect(MobEffects.WITHER);

        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, false, true, true));

        sp.displayClientMessage(
                Component.translatable("ritual.loopypowers.severance.complete")
                        .withStyle(ChatFormatting.RED),
                true
        );
    }
}