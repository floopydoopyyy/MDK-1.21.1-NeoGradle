package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.entity.BlackHoleEntity;
import com.loopy.loopypowers.network.ClientPowerState;
import com.loopy.loopypowers.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleBlackHoleParticles(BlackHoleParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;
            Entity entity = level.getEntity(payload.entityId());
            if (entity instanceof BlackHoleEntity bh) {
                bh.spawnClientParticles(payload.lifeProgress());
            }
        });
    }

    public static void handleFateAura(FateAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FateAuraClient.update(payload.entityId(), payload.storedDamage(), payload.timerTicks())
        );
    }

    public static void handleBloodWhip(BloodWhipPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                BloodFxClient.spawnWhip(payload.entityId(), payload.x(), payload.y(), payload.z())
        );
    }

    public static void handleBloodBind(BloodBindPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                BloodFxClient.updateBind(payload.casterId(), payload.targetId(), payload.active())
        );
    }

    public static void handleBlackoutFx(BlackoutFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                DarknessFxClient.spawnBlackout(payload.x(), payload.y(), payload.z())
        );
    }

    public static void handleFractureFx(FractureFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                DimensionalFxClient.updateFracture(
                        payload.ownerId(), payload.x(), payload.y(), payload.z(), payload.seed(), payload.active())
        );
    }

    public static void handleExplosionDropZone(ExplosionDropZonePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                ExplosionFxClient.updateDropZone(payload.ownerId(), payload.active(), payload.stage())
        );
    }

    public static void handleFireUltFx(FireUltPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FireFxClient.updateUlt(payload.ownerId(), payload.active())
        );
    }

    public static void handleDuelBeam(DuelBeamPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FortuneFxClient.spawnDuelBeam(payload.start(), payload.end())
        );
    }

    public static void handleDuelTether(DuelTetherPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FortuneFxClient.spawnDuelTether(payload.entityAId(), payload.entityBId())
        );
    }

    public static void handleDuelAura(DuelAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FortuneFxClient.spawnDuelAura(payload.entityId())
        );
    }

    public static void handleHouseRoofFx(HouseRoofFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FortuneFxClient.spawnHouseRoofFx(payload.cx(), payload.cz(), payload.y(), payload.radius())
        );
    }

    // ----------------------------------------------------------------
    // Flight Boom FX
    // ----------------------------------------------------------------

    public static void handleFlightBoomWindup(FlightBoomWindupPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FlightBoomFxClient.onWindup(payload.entityId(), payload.isStart())
        );
    }

    public static void handleFlightBoomDash(FlightBoomDashPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FlightBoomFxClient.onDash(
                        payload.entityId(),
                        payload.dir().x, payload.dir().y, payload.dir().z,
                        payload.isStart()
                )
        );
    }

    public static void handleFlightBoomImpact(FlightBoomImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                FlightBoomFxClient.onImpact(payload.pos().x, payload.pos().y, payload.pos().z)
        );
    }

    public static void handleHealingUlt(HealingUltPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                HealingUltFxClient.onUltTick(
                        payload.entityId(), payload.phase(), payload.ultTicks(), payload.isPhaseChange()
                )
        );
    }

    public static void handleStormCloud(StormCloudPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                StormCloudFxClient.onStormCloudTick(
                        payload.entityId(), payload.stormTicks(), payload.isActivation()
                )
        );
    }

    public static void handleNatureGasFx(NatureGasFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                NatureFxClient.spawnGasFx(payload.center(), payload.radius(), payload.halfHeight(), payload.count(), payload.seed())
        );
    }

    public static void handleNatureTetherFx(NatureTetherFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                NatureFxClient.spawnTetherFx(payload.from(), payload.to(), payload.seed())
        );
    }

    public static void handleNatureCageFx(NatureCageFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                NatureFxClient.spawnCageFx(payload.cx(), payload.cy(), payload.cz(), payload.radius(), payload.thickness())
        );
    }

    public static void handleIceSpikeTrail(IceSpikeTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onSpikeTrail(
                        payload.startX(), payload.startZ(),
                        payload.targetX(), payload.targetZ(),
                        payload.progress(), payload.seed(), payload.yHint()
                )
        );
    }

    public static void handleIceSpikePuff(IceSpikePuffPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onSpikePuff(payload.x(), payload.y(), payload.z())
        );
    }

    public static void handleIceFreezeStage(IceFreezeStagePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onFreezeStage(payload.entityId(), payload.stage(), payload.gameTime())
        );
    }

    public static void handleIceShatter(IceShatterPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onShatter(payload.entityId(), payload.x(), payload.y(), payload.z())
        );
    }

    public static void handleIceBeamCharge(IceBeamChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onBeamCharge(payload.entityId(), payload.tickCount())
        );
    }

    public static void handleIceBeamFire(IceBeamFirePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onBeamFire(
                        payload.startX(), payload.startY(), payload.startZ(),
                        payload.endX(), payload.endY(), payload.endZ(),
                        payload.gameTime()
                )
        );
    }

    public static void handleIceBlizzard(IceBlizzardPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                IceFxClient.onBlizzard(payload.entityId(), payload.ultTick())
        );
    }
}