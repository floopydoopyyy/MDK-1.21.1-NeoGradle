package com.loopy.loopypowers.ritual;

import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.payload.ElementalRitualTickPayload;
import com.loopy.loopypowers.power.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public class ElementalRitual implements RitualInterface {

    private static final List<Supplier<PowerInterface>> ELEMENTAL_POWERS = List.of(
            FirePower::new,
            IcePower::new,
            LightningPower::new
    );

    // Keep logical stuff on server
    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private final Player player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public ElementalRitual(Player player, RitualManager.RitualType type) {
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

        // SEND NETWORK PAYLOAD TO NEARBY CLIENTS
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new ElementalRitualTickPayload(sp.getId(), ticks));

        int stage = currentStage();
        if (stage < 4) lockPosition(sp);

        // Server only handles logical stage events (damage, potions, powers)
        switch (stage) {
            case 4 -> tickStage4ServerOnly(sp, world);
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

    private void tickStage4ServerOnly(ServerPlayer sp, ServerLevel world) {
        int stageTick = ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
        double progress = (double) stageTick / STAGE_4_TICKS;

        if (progress > 0.15f && progress < 0.88f) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, true, false, false));
        }

        if (stageTick % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.hurt(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }
    }

    private void onComplete(ServerPlayer sp, ServerLevel world) {
        Supplier<PowerInterface> factory = ELEMENTAL_POWERS.get(sp.getRandom().nextInt(ELEMENTAL_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeEffect(MobEffects.LEVITATION);
        sp.removeEffect(MobEffects.BLINDNESS);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.clearFire();
        sp.heal(3.0f);
    }
}