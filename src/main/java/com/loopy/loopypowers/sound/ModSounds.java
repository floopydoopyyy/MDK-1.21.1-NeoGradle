package com.loopy.loopypowers.sound;

import com.loopy.loopypowers.Loopypowers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    // create the DeferredRegister for Sound Events
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Loopypowers.MOD_ID);

    // helper method now returns a DeferredHolder and uses ResourceLocation
    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, name)));
    }

    // 3. Register all your sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDERCLAP     = registerSoundEvent("thunderclap");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORTCLAP    = registerSoundEvent("teleportclap");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORTSNAP    = registerSoundEvent("teleportsnap");
    public static final DeferredHolder<SoundEvent, SoundEvent> DASH            = registerSoundEvent("dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUSHSTART       = registerSoundEvent("rushstart");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUSHLOOP        = registerSoundEvent("rushloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERDRIVESTART  = registerSoundEvent("overdrivestart");
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERDRIVELOOP   = registerSoundEvent("overdriveloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRICITY     = registerSoundEvent("electricity");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAGE            = registerSoundEvent("rage");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASSDROP        = registerSoundEvent("bassdrop");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASSSINGLE      = registerSoundEvent("basssingle");
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLRUSH        = registerSoundEvent("bullrush");
    public static final DeferredHolder<SoundEvent, SoundEvent> EARRING         = registerSoundEvent("earring");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENTITYSLAM      = registerSoundEvent("entityslam");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN         = registerSoundEvent("railgun");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM            = registerSoundEvent("slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALLSLAM        = registerSoundEvent("wallslam");
    public static final DeferredHolder<SoundEvent, SoundEvent> POSSESSION      = registerSoundEvent("possession");
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLRUSHSTOP    = registerSoundEvent("bullrushstop");
    public static final DeferredHolder<SoundEvent, SoundEvent> LUNGESTART      = registerSoundEvent("lungestart");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHATTER         = registerSoundEvent("shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCK           = registerSoundEvent("shock");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKSTAB        = registerSoundEvent("backstab");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIGSTAB         = registerSoundEvent("bigstab");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASSBURST       = registerSoundEvent("bassburst");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARENABUILD      = registerSoundEvent("arenabuild");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT            = registerSoundEvent("bolt");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLODEBIG      = registerSoundEvent("explodebig");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRAY           = registerSoundEvent("spray");
    public static final DeferredHolder<SoundEvent, SoundEvent> VINELASH        = registerSoundEvent("vinelash");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARDLOOP    = registerSoundEvent("blizzardloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIKECAST       = registerSoundEvent("spikecast");
    public static final DeferredHolder<SoundEvent, SoundEvent> DARKNESSLOOP    = registerSoundEvent("darknessloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICEBEAMLOOP     = registerSoundEvent("icebeamloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICEBEAMCHARGE   = registerSoundEvent("icebeamcharge");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAISESTAKES     = registerSoundEvent("raisestakes");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALLIN           = registerSoundEvent("allin");
    public static final DeferredHolder<SoundEvent, SoundEvent> JACKPOT         = registerSoundEvent("jackpot");
    public static final DeferredHolder<SoundEvent, SoundEvent> JACKPOT2        = registerSoundEvent("jackpot2");
    public static final DeferredHolder<SoundEvent, SoundEvent> JACKPOT3        = registerSoundEvent("jackpot3");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEWRULE         = registerSoundEvent("newrule");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICKER         = registerSoundEvent("flicker");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICKER2        = registerSoundEvent("flicker2");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICKER3        = registerSoundEvent("flicker3");
    public static final DeferredHolder<SoundEvent, SoundEvent> DARKNESSTELEPORT= registerSoundEvent("darknessteleport");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISTENTER       = registerSoundEvent("mistenter");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISTLOOP        = registerSoundEvent("mistloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> DARKNESSTELEPORT2= registerSoundEvent("darknessteleport2");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUSPEND         = registerSoundEvent("suspend");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACKHOLELOOP   = registerSoundEvent("blackholeloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> YANK            = registerSoundEvent("yank");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUST            = registerSoundEvent("gust");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPDRAFT         = registerSoundEvent("updraft");
    public static final DeferredHolder<SoundEvent, SoundEvent> RITUALLOOP      = registerSoundEvent("ritualloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> RITUALSTART     = registerSoundEvent("ritualstart");
    public static final DeferredHolder<SoundEvent, SoundEvent> COSMICRAY       = registerSoundEvent("cosmicray");
    public static final DeferredHolder<SoundEvent, SoundEvent> JACKPOTFUNNY    = registerSoundEvent("jackpotfunny");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARTYPOPPER     = registerSoundEvent("partypopper");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUNNYFNAF       = registerSoundEvent("funnyfnaf");
    public static final DeferredHolder<SoundEvent, SoundEvent> ONEPUNCH        = registerSoundEvent("onepunch");
    public static final DeferredHolder<SoundEvent, SoundEvent> HECANFLY        = registerSoundEvent("hecanfly");
    public static final DeferredHolder<SoundEvent, SoundEvent> PVZPOP          = registerSoundEvent("pvzpop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MEDIC           = registerSoundEvent("medic");
    public static final DeferredHolder<SoundEvent, SoundEvent> STINK           = registerSoundEvent("stink");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHY             = registerSoundEvent("why");

    private ModSounds() {}

    // Eexpose a method to attach the DeferredRegister to the main Event Bus
    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}