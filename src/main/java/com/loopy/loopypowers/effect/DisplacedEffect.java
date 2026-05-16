package com.loopy.loopypowers.effect;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Vector3f;

import java.util.Set;

@EventBusSubscriber(modid = "loopypowers")
public class DisplacedEffect extends MobEffect {

    public DisplacedEffect() {
        super(MobEffectCategory.HARMFUL, 0x00FFFF); // cyan
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // Apply every tick
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        ServerLevel level = (ServerLevel) entity.level();

        // set velocity to 0
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        entity.fallDistance = 0;

        // Lock position using packets (no more camera lock)
        if (entity instanceof ServerPlayer displacedPlayer) {
            displacedPlayer.connection.send(
                    new ClientboundPlayerPositionPacket(
                            displacedPlayer.getX(),
                            displacedPlayer.getY(),
                            displacedPlayer.getZ(),
                            0f,
                            0f,
                            Set.of(
                                    RelativeMovement.X_ROT,
                                    RelativeMovement.Y_ROT
                            ),
                            0
                    )
            );
        }

        // invisibility
        entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, true, false, false));

        // stop damage
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 255, true, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 255, true, false, false));

        // disable mob ai
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }

        // fx
        DustParticleOptions darkBlue  = new DustParticleOptions(new Vector3f(0.05f, 0.1f, 0.4f),  1.4f);
        DustParticleOptions midBlue   = new DustParticleOptions(new Vector3f(0.2f,  0.4f, 1.0f),  1.2f);
        DustParticleOptions brightBlue = new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1.0f),  0.9f);

        double x = entity.getX();
        double y = entity.getY(0.5);
        double z = entity.getZ();

        level.sendParticles(brightBlue,  x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
        level.sendParticles(midBlue,     x, y, z, 4, 0.35, 0.55, 0.35, 0.02);

        if (level.random.nextFloat() < 0.4f) {
            level.sendParticles(darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        }

        return true;
    }

    // ============================================================
    // NEOFORGE EVENTS
    // ============================================================

    // give ai back on expiration
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().value() instanceof DisplacedEffect) {
            if (event.getEntity() instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }
    }

    // give ai back when cleared early
    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().value() instanceof DisplacedEffect) {
            if (event.getEntity() instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }
    }

    // deny all attacks
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
        }
    }

    // deny item usage
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
        }
    }

    // deny block interaction
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
        }
    }

    // deny block breaking
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
        }
    }

    // deny entity interaction (villagers, riding mounts, shearing)
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
        }
    }
}