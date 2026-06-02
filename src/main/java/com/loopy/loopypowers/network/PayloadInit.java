package com.loopy.loopypowers.network;

import com.loopy.loopypowers.network.payload.*;

/**
 * Forces every client-bound payload class to load before
 * RegisterPayloadHandlersEvent fires, so their static blocks
 * have run and added themselves to ClientPayloadRegistry.
 *
 * HOW TO ADD A NEW PAYLOAD:
 *   1. Add the static block to your payload class (copy from any existing one)
 *   2. Add one line here: var _x = YourNewPayload.TYPE;
 *   That's it — nothing else needs changing.
 */
public final class PayloadInit {

    private PayloadInit() {}

    public static void init() {
        var _cs  = AbilityPackets.CameraShakePayload.ID;
        var _ss  = AbilityPackets.SyncStrengthPayload.ID;
        var _hp   = AbilityPackets.HidePlayerPayload.ID;
        var _sa   = AbilityPackets.StunAudioPayload.ID;
        var _rt   = AbilityPackets.ResonanceTrailPayload.ID;
        var _rr   = AbilityPackets.ResonanceRingPayload.ID;
        var _rl   = AbilityPackets.ResonanceLinePayload.ID;
        var _bh   = BlackHoleParticlePayload.TYPE;
        var _sc   = StormCloudPayload.TYPE;
        var _bb   = BloodBindPayload.TYPE;
        var _bw   = BloodWhipPayload.TYPE;
        var _box  = BlackoutFxPayload.TYPE;
        var _ca   = CompelAuraPayload.TYPE;
        var _ch   = CompelHitPayload.TYPE;
        var _ct   = CompelTickPayload.TYPE;
        var _da   = DuelAuraPayload.TYPE;
        var _db   = DuelBeamPayload.TYPE;
        var _dt   = DuelTetherPayload.TYPE;
        var _edz  = ExplosionDropZonePayload.TYPE;
        var _fat  = FateAuraPayload.TYPE;
        var _fu   = FireUltPayload.TYPE;
        var _ert  = ElementalRitualTickPayload.TYPE;
        var _fbd  = FlightBoomDashPayload.TYPE;
        var _fbi  = FlightBoomImpactPayload.TYPE;
        var _fbw  = FlightBoomWindupPayload.TYPE;
        var _ffx  = FractureFxPayload.TYPE;
        var _hu   = HealingUltPayload.TYPE;
        var _hrf  = HouseRoofFxPayload.TYPE;
        var _ibc  = IceBeamChargePayload.TYPE;
        var _ibf  = IceBeamFirePayload.TYPE;
        var _lrt  = LifeRitualTickPayload.TYPE;
        var _mrt  = MotionRitualTickPayload.TYPE;
        var _mnd  = MindRitualTickPayload.TYPE;
        var _pur  = PerfectedUpgradeRitualTickPayload.TYPE;
        var _ish  = IceShatterPayload.TYPE;
        var _ifs  = IceFreezeStagePayload.TYPE;
        var _ibl  = IceBlizzardPayload.TYPE;
        var _ist  = IceSpikeTrailPayload.TYPE;
        var _isp  = IceSpikePuffPayload.TYPE;
        var _lc   = LightningClapPayload.TYPE;
        var _lch  = LightningClapHitPayload.TYPE;
        var _lsa  = LightningSuperchargeAuraPayload.TYPE;
        var _lsb  = LightningSuperchargeBurstPayload.TYPE;
        var _ncf  = NatureCageFxPayload.TYPE;
        var _ngf  = NatureGasFxPayload.TYPE;
        var _ntf  = NatureTetherFxPayload.TYPE;
        var _pa   = PossessedAuraPayload.TYPE;
        var _prt  = PowerRitualTickPayload.TYPE;
        var _pur2 = PowerUpgradeRitualTickPayload.TYPE;
        var _pl   = PsychicLeechPayload.TYPE;
        var _psb  = PsychicSpikeBeamPayload.TYPE;
        var _psi  = PsychicSpikeImpactPayload.TYPE;
        var _ph   = PuppetryHitPayload.TYPE;
        var _ptk  = PuppetryTickPayload.TYPE;
        var _rrt  = RuinRitualTickPayload.TYPE;
        var _srt  = SeveranceRitualTickPayload.TYPE;
        var _sba  = SoundBassBlastAnimatePayload.TYPE;
        var _spa  = SoundBassPullAnimatePayload.TYPE;
        var _sbb  = SoundBassBurstPayload.TYPE;
        var _sbp  = SoundBassPullPayload.TYPE;
        var _spu  = SoundBassPulsePayload.TYPE;
        var _sub  = SoundUltimateBeamPayload.TYPE;
        var _ska  = SpikeAuraPayload.TYPE;
        var _spp  = SpeedPinballAnchorPayload.TYPE;
        var _sot  = SpeedOverdriveTrailPayload.TYPE;
        var _soc  = SpeedOverdriveCastPayload.TYPE;
        var _slh  = SpeedLowHealthBurstPayload.TYPE;
        var _sef  = SpeedExplosionFxPayload.TYPE;
        var _sdt  = SpeedDashTrailPayload.TYPE;
        var _sdc  = SpeedDashCastPayload.TYPE;
        var _spr  = SpaceRitualTickPayload.TYPE;
        var _str  = StrengthParticlePayload.TYPE;
        var _tkp  = TelekinesisParticlePayload.TYPE;
        var _tpp  = TeleportParticlePayload.TYPE;
    }
}