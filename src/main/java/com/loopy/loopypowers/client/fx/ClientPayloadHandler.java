package com.loopy.loopypowers.client.fx;

import com.loopy.loopypowers.entity.BlackHoleEntity;
import com.loopy.loopypowers.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

    public static void handlePuppetryTick(PuppetryTickPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onPuppetryTick(
                        payload.x(), payload.y(), payload.z(),
                        payload.vx(), payload.vy(), payload.vz(),
                        payload.life()
                )
        );
    }

    public static void handlePuppetryHit(PuppetryHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onPuppetryHit(
                        payload.targetEntityId(),
                        payload.projX(), payload.projY(), payload.projZ(),
                        payload.life()
                )
        );
    }

    public static void handleCompelTick(CompelTickPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onCompelTick(
                        payload.x(), payload.y(), payload.z(),
                        payload.vx(), payload.vy(), payload.vz(),
                        payload.life()
                )
        );
    }

    public static void handleCompelHit(CompelHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onCompelHit(payload.targetEntityId(), payload.life())
        );
    }
    public static void handleCompelAura(CompelAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onCompelAura(payload.entityId())
        );
    }

    public static void handleSpikeAura(SpikeAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onSpikeAura(payload.entityId())
        );
    }

    public static void handlePossessedAura(PossessedAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onPossessedAura(payload.entityId())
        );
    }

    public static void handleSpikeImpact(PsychicSpikeImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onSpikeImpact(payload.entityId())
        );
    }

    public static void handleSpikeBeam(PsychicSpikeBeamPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onSpikeBeam(payload.start(), payload.end(), payload.isChain())
        );
    }

    public static void handleLeech(PsychicLeechPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PsychicFxClient.onLeech(payload.targetId(), payload.casterId())
        );
    }

    public static void handleLightningClap(LightningClapPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LightningFxClient.onClap(payload.entityId(), payload.isParty())
        );
    }

    public static void handleLightningClapHit(LightningClapHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LightningFxClient.onClapHit(payload.targetId(), payload.t(), payload.isParty())
        );
    }

    public static void handleLightningSuperchargeBurst(LightningSuperchargeBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LightningFxClient.onSuperchargeBurst(payload.entityId())
        );
    }

    public static void handleLightningSuperchargeAura(LightningSuperchargeAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LightningFxClient.onSuperchargeAura(payload.entityId())
        );
    }

    public static void handleSpeedPinballAnchor(SpeedPinballAnchorPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onPinballAnchor(payload.anchorX(), payload.anchorY(), payload.anchorZ())
        );
    }

    public static void handleSpeedDashCast(SpeedDashCastPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onDashCast(
                        payload.x(), payload.y(), payload.z(),
                        payload.lookX(), payload.lookY(), payload.lookZ()
                )
        );
    }

    public static void handleSpeedDashTrail(SpeedDashTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onDashTrail(
                        payload.x(), payload.y(), payload.z(),
                        payload.vx(), payload.vy(), payload.vz(),
                        payload.gameTime()
                )
        );
    }

    public static void handleSpeedOverdriveCast(SpeedOverdriveCastPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onOverdriveCast(payload.x(), payload.y(), payload.z())
        );
    }

    public static void handleSpeedOverdriveTrail(SpeedOverdriveTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onOverdriveTrail(
                        payload.x(), payload.y(), payload.z(),
                        payload.vx(), payload.vy(), payload.vz(),
                        payload.gameTime()
                )
        );
    }

    public static void handleSpeedLowHealthBurst(SpeedLowHealthBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onLowHealthBurst(payload.x(), payload.y(), payload.z())
        );
    }

    public static void handleSpeedExplosionFx(SpeedExplosionFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                SpeedFxClient.onExplosionFx(
                        payload.x(), payload.y(), payload.z(),
                        payload.heavy()
                )
        );
    }

    public static void handleSoundBassPull(
            SoundBassPullPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                SoundPowerClient.spawnPullContractingSphere(
                        payload.center(),
                        payload.seed()
                )
        );
    }

    public static void handleSoundBassBurst(
            SoundBassBurstPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {

            SoundPowerClient.spawnFinalExpandingSphere(
                    payload.center(),
                    payload.seed()
            );

            SoundPowerClient.spawnBassGroundRing(
                    payload.center(),
                    payload.radius(),
                    payload.seed()
            );
        });
    }

    public static void handleSoundBassPullAnimate(
            SoundBassPullAnimatePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                SoundPowerClient.animateBassPullSphere(
                        payload.center(),
                        payload.step(),
                        payload.seed()
                )
        );
    }

    public static void handleSoundBassBlastAnimate(
            SoundBassBlastAnimatePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                SoundPowerClient.animateBassBlastSphere(
                        payload.center(),
                        payload.step(),
                        payload.seed()
                )
        );
    }

    public static void handleSoundUltimateBeam(
            SoundUltimateBeamPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                SoundPowerClient.spawnUltimateBeam(
                        payload.start(),
                        payload.end(),
                        payload.seed()
                )
        );
    }

    public static void handleSoundBassPulse(
            SoundBassPulsePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                SoundPowerClient.spawnBassPulse(
                        payload.pos()
                )
        );
    }

    public static void handleStrengthParticles(StrengthParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                StrengthFxClient.onParticleEvent(payload)
        );
    }

    public static void handleTeleportParticles(TeleportParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                TeleportFxClient.onParticleEvent(payload)
        );
    }

    public static void handleTelekinesisParticles(TelekinesisParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                TelekinesisFxClient.onParticleEvent(payload)
        );
    }

    public static void handleElementalRitual(final ElementalRitualTickPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null && context.player().level() != null) {
                Entity entity = context.player().level().getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    ElementalRitualClient.tick(player.level(), player, payload.ticks());
                }
            }
        });
    }
}