package com.loopy.loopypowers;

import com.loopy.loopypowers.block.ModBlocks;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.item.ModItemGroups;
import com.loopy.loopypowers.item.ModItems;
import com.loopy.loopypowers.manager.ModAttachments;
import com.loopy.loopypowers.manager.PlayerDataStore;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.network.PayloadInit;
import com.loopy.loopypowers.network.payload.*;
import com.loopy.loopypowers.power.*;
import com.loopy.loopypowers.ritual.RitualManager;
import com.loopy.loopypowers.sound.ModSounds;
import com.loopy.loopypowers.ui.CooldownUI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// NeoForge entry point: @Mod replaces ModInitializer.
// The constructor receives the mod-bus (for registry/setup events) via injection.
// Game-world events are registered on NeoForge.EVENT_BUS.
@Mod(Loopypowers.MOD_ID)
public class Loopypowers {

    public static final String MOD_ID = "loopypowers";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    public Loopypowers(IEventBus modEventBus) {
        PayloadInit.init();

        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEffects.register(modEventBus);
        ModItemGroups.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // ── Lifecycle setup (runs after registries are frozen) ──
        modEventBus.addListener(this::commonSetup);
        // ── Game-world event listeners ──
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Loopypowers loaded!");
    }

    /* ============================================================
       SETUP
       ============================================================ */

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModDamageTypes::init);
    }

    /* ============================================================
       PLAYER LIFECYCLE EVENTS
       ============================================================ */

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Attachment data is already loaded by NeoForge at this point.
            // load() just calls onPlayerLoad to re-apply passive effects and sync the client.
            PlayerDataStore.load(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // save() is a no-op for per-player data (attachment handles it automatically).
            // We keep the call so nothing breaks if you later add global-per-player saves.
            PlayerDataStore.save(player);
            // Clear in-memory cooldown maps to prevent memory leaks on disconnect.
            PowerManager.clearPlayerState(player);
        }
    }

    /**
     * Fires on both death (isWasDeath=true) and dimension change (isWasDeath=false).
     *
     * The PlayerPowerData attachment is registered with copyOnDeath(), so NeoForge
     * copies the attachment from oldPlayer to newPlayer automatically before this
     * event fires. We must NOT call setPower/setLevel here — that would trigger
     * onRemove on the already-copied power and then onAssign a second time.
     *
     * Instead we just copy in-memory cooldowns (which are not in the attachment)
     * and call onPlayerLoad to re-apply passive effects on the new entity.
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;
        if (!(event.getEntity()   instanceof ServerPlayer newPlayer)) return;

        // In-memory cooldown timestamps live in PowerManager's static map, not in
        // the attachment, so they must be transferred manually.
        PowerManager.copyCooldowns(oldPlayer, newPlayer);

        // Re-apply passive effects and sync the client for the new player entity.
        // Attachment data (power name, level, cd mult, passive toggle) is already
        // present on newPlayer courtesy of NeoForge's copyOnDeath copy.
        PowerManager.onPlayerLoad(newPlayer);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PowerInterface power = PowerManager.getPower(sp);
            if (power != null) {
                power.onDeath(sp);
            }
        }
    }

    /* ============================================================
       COMBAT EVENTS
       ============================================================ */

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        PowerInterface power = PowerManager.getPower(sp);
        if (power != null) power.onHit(sp, target);

        // Don't cancel: let vanilla handle the physical hit as normal.
        // Individual powers cancel via the damage hook below if needed.
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        var source = event.getSource();
        float amount = event.getAmount();

        // -- GLOBAL EVENTS --

        if (victim.hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
            return;
        }
        if (source.getEntity() instanceof LivingEntity attacker && attacker.hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
            return;
        }

        // Fall damage immunity (Braced)
        if (source.is(DamageTypeTags.IS_FALL)) {
            if (victim.hasEffect(ModEffects.BRACED)) {
                victim.removeEffect(ModEffects.BRACED);
                if (victim.level() instanceof ServerLevel w) {
                    w.playSound(null, victim.blockPosition(), SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 0.7f, 1.2f);
                    w.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY(), victim.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
                }
                event.setCanceled(true);
                return;
            }
        }

        // Psychic Power Global Hook
        if (PsychicPower.onDamageGlobal(victim, source)) {
            event.setCanceled(true);
            return;
        }

        // Telekinesis Power Global Hook
        float tkAmount = TelekinesisPower.onDamageGlobal(victim, source, amount);
        if (tkAmount != amount) {
            amount = tkAmount;
            event.setAmount(amount);
        }

        // ── ATTACKER-SIDE ─────────────────────────────────────────────────

        if (FortunePower.tryAdjustFortuneDamage(victim, source, amount)) {
            event.setCanceled(true);
            return;
        }

        if (source.getEntity() instanceof ServerPlayer attacker) {
            PowerInterface attackerPower = PowerManager.getPower(attacker);

            if (attackerPower != null) {
                if (!attackerPower.onAttack(attacker, victim, source, amount)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // ── VICTIM-SIDE ───────────────────────────────────────────────────

        if (!(victim instanceof ServerPlayer victimPlayer)) return;
        PowerInterface victimPower = PowerManager.getPower(victimPlayer);

        if (victimPower != null) {
            if (!victimPower.onDamaged(victimPlayer, source, amount)) {
                event.setCanceled(true);
            }
        }
    }

    /* ============================================================
       SERVER TICK
       ============================================================ */

    @SubscribeEvent
    public void onServerTickEnd(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        RitualManager.tick(server);

        for (ServerLevel world : server.getAllLevels()) {
            FortunePower.tickHousesWorld(world);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayer player) {
        CooldownUI.tick(player);

        PowerInterface power = PowerManager.getPower(player);
        if (power != null) {
            power.onTick(player);
        }
    }
}