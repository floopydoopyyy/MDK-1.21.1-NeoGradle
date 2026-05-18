package com.loopy.loopypowers;

import com.loopy.loopypowers.block.ModBlocks;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.effect.ModEffects;
import com.loopy.loopypowers.entity.ModEntities;
import com.loopy.loopypowers.item.ModItems;
import com.loopy.loopypowers.manager.PlayerDataStore;
import com.loopy.loopypowers.manager.PowerManager;
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

        // ── Deferred registers: each must subscribe to the MOD event bus ──
        // The individual register() methods should internally call
        // DeferredRegister.register(modEventBus) — update each helper class
        // to accept an IEventBus parameter if they don't already.
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        // ModItems.register() is a plain class-loading hook with no IEventBus param;
        // items piggyback on ModBlocks.ITEMS so just touch the class to load it.
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEffects.register(modEventBus);

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

    // ServerPlayConnectionEvents.JOIN -> PlayerEvent.PlayerLoggedInEvent
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { // ServerPlayerEntity -> ServerPlayer
            PlayerDataStore.load(player);
        }
    }

    // ServerPlayConnectionEvents.DISCONNECT -> PlayerEvent.PlayerLoggedOutEvent
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataStore.save(player);
            // Prevent memory leaks on disconnect
            PowerManager.clearPlayerState(player);
        }
    }

    // ServerPlayerEvents.COPY_FROM -> PlayerEvent.Clone
    // event.isWasDeath(): true = player died; false = dimension change.
    // The original code didn't branch on `alive`, so no logic change is needed.
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;
        if (!(event.getEntity()   instanceof ServerPlayer newPlayer)) return;

        PowerInterface oldPower = PowerManager.getPower(oldPlayer); // Power -> PowerInterface
        if (oldPower != null) {
            // Silently assign so we don't spam them with text on respawn
            PowerManager.setPower(newPlayer, oldPower, true);
        }

        int level = PowerManager.getLevel(oldPlayer);
        PowerManager.setLevel(newPlayer, level);
        PowerManager.copyCooldowns(oldPlayer, newPlayer);
        PlayerDataStore.save(newPlayer);
    }

    // ServerLivingEntityEvents.AFTER_DEATH -> LivingDeathEvent
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

    // AttackEntityCallback.EVENT -> AttackEntityEvent
    // In NeoForge this fires server-side when a player swings at an entity,
    // before vanilla damage is applied. Cancel it to suppress the vanilla hit.
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        PowerInterface power = PowerManager.getPower(sp);
        if (power != null) power.onHit(sp, target);

        // Don't cancel: let vanilla handle the physical hit as normal.
        // Individual powers cancel via the damage hook below if needed.
    }

    // ServerLivingEntityEvents.ALLOW_DAMAGE -> LivingIncomingDamageEvent
    //
    // Key simplification: the Fabric hasCustomEffect / removeCustomEffect helpers
    // were workarounds for Fabric's registry-entry wrapping. In NeoForge,
    // ModEffects.X are DeferredHolder<MobEffect, ?> which implement Holder<MobEffect>,
    // so entity.hasEffect(ModEffects.X) and entity.removeEffect(ModEffects.X)
    // work directly — no helpers needed.
    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        var source = event.getSource();
        float amount = event.getAmount();

        // -- GLOBAL EVENTS --

        // keep displace immunity at top — fast fail
        // hasCustomEffect(victim, ModEffects.DISPLACED) -> victim.hasEffect(ModEffects.DISPLACED)
        if (victim.hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
            return;
        }
        // source.getAttacker() -> source.getEntity()
        if (source.getEntity() instanceof LivingEntity attacker && attacker.hasEffect(ModEffects.DISPLACED)) {
            event.setCanceled(true);
            return;
        }

        // Fall damage immunity (Braced)
        // source.isIn(tag) -> source.is(tag), net.minecraft.registry.tag -> net.minecraft.tags
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

    // ServerTickEvents.END_SERVER_TICK -> ServerTickEvent.Post
    @SubscribeEvent
    public void onServerTickEnd(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        RitualManager.tick(server);

        // server.getWorlds() -> server.getAllLevels()
        for (ServerLevel world : server.getAllLevels()) {
            FortunePower.tickHousesWorld(world);
        }

        // server.getPlayerManager().getPlayerList() -> server.getPlayerList().getPlayers()
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