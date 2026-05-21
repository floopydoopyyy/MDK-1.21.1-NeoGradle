package com.loopy.loopypowers.power;

import com.loopy.loopypowers.block.ModBlocks;
import com.loopy.loopypowers.damage.ModDamageTypes;
import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.sound.ModSounds;
import com.loopy.loopypowers.ui.CooldownUI;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;
import com.loopy.loopypowers.network.payload.DuelBeamPayload;
import com.loopy.loopypowers.network.payload.DuelTetherPayload;
import com.loopy.loopypowers.network.payload.DuelAuraPayload;

import java.util.*;

public class FortunePower implements PowerInterface {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, FortuneState> ACTIVE_STATES = new HashMap<>();
    private static final Set<UUID> DAMAGE_GUARDS = new HashSet<>();

    private static class FortuneState {
        int luck = 0;
        int luckDecayTicks = 0;
    }

    private static FortuneState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUUID(), k -> new FortuneState());
    }

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("fo_")); // Cleanup legacy tags
        ACTIVE_STATES.put(player.getUUID(), new FortuneState());

        breakDuel(player.getUUID());

        var server = player.getServer();
        removeHouseNow(server, player.getUUID());
    }

    @Override
    public void onRemove(ServerPlayer player) {
        player.getTags().removeIf(tag -> tag.startsWith("fo_"));
        ACTIVE_STATES.remove(player.getUUID());

        breakDuel(player.getUUID());

        var server = player.getServer();
        removeHouseNow(server, player.getUUID());
    }

    @Override
    public void onDeath(ServerPlayer player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        if (!player.isAlive()) return;

        FortuneState state = getState(player);
        tickLuck(state);
        tickDuelsWorld(player.serverLevel());
    }

    @Override
    public void onHit(ServerPlayer attacker, LivingEntity target) {
        FortuneState state = getState(attacker);
        addLuck(attacker, state, LUCK_GAIN_ON_HIT);
        tryProcOnHit(attacker, state, target);
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final int LUCK_MAX = 100;
    private static final int PROC_ACTIONBAR_TICKS = 35;

    // how fast you build luck per hit
    private static final int LUCK_GAIN_ON_HIT = 6;

    // decay after you stop hitting
    private static final int LUCK_DECAY_DELAY_TICKS = 30;   // 3s after last hit
    private static final int LUCK_DECAY_STEP_TICKS  = 10;   // then decay every 0.5s
    private static final int LUCK_DECAY_STEP_POINTS = 5;

    // proc chance scales with luck
    private static final float PROC_BASE = 0.04f;           // 4%
    private static final float PROC_BONUS_AT_MAX = 0.18f;   // keep in mind base is separate from this and added to this total

    // spend luck on proc
    private static final int LUCK_SPEND_ON_PROC = 45;

    private static void addLuck(ServerPlayer p, FortuneState state, int add) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(p)) return;
        if (add <= 0) return;

        state.luck = Mth.clamp(state.luck + add, 0, LUCK_MAX);
        state.luckDecayTicks = LUCK_DECAY_DELAY_TICKS;
    }

    private static void tickLuck(FortuneState state) {
        if (state.luck <= 0) return;

        state.luckDecayTicks--;

        // when delay hits 0, step down and schedule next step
        if (state.luckDecayTicks == 0) {
            state.luck = Math.max(0, state.luck - LUCK_DECAY_STEP_POINTS);
            if (state.luck > 0) {
                state.luckDecayTicks = LUCK_DECAY_STEP_TICKS;
            }
        }
    }

    private static void tryProcOnHit(ServerPlayer attacker, FortuneState state, LivingEntity target) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        ServerLevel w = attacker.serverLevel();

        float t = state.luck / (float) LUCK_MAX;
        float chance = PROC_BASE + (PROC_BONUS_AT_MAX * t);

        if (w.random.nextFloat() >= chance) return;

        // spend luck when we proc
        state.luck = Math.max(0, state.luck - LUCK_SPEND_ON_PROC);
        state.luckDecayTicks = LUCK_DECAY_DELAY_TICKS;

        int roll = w.random.nextInt(3);

        if (roll == 0) {
            //extra damage
            float extra = 1.5f + w.random.nextFloat() * 2.5f; // 1.5..4.0
            target.hurt(attacker.damageSources().playerAttack(attacker), extra);

            // actionbar
            CooldownUI.pushActionbarOverride(attacker, Component.translatable("power.loopypowers.fortune.jackpot.lucky_shot"), PROC_ACTIONBAR_TICKS);

            // particles on enemy
            spawnProcParticlesEnemy(w, target);

            // sound
            w.playSound(null, target.blockPosition(),
                    w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY.get() : ModSounds.JACKPOT.get(),
                    attacker.getSoundSource(),
                    0.5f, 1.0f);

        } else if (roll == 1) {
            //debuff enemy
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false));

            CooldownUI.pushActionbarOverride(attacker, Component.translatable("power.loopypowers.fortune.jackpot.bad_beat"), PROC_ACTIONBAR_TICKS);

            spawnProcParticlesEnemy(w, target);

            w.playSound(null, target.blockPosition(),
                    w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY.get() : ModSounds.JACKPOT2.get(),
                    attacker.getSoundSource(),
                    0.5f, 1.0f);

        } else {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            attacker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0, true, false));

            CooldownUI.pushActionbarOverride(attacker, Component.translatable("power.loopypowers.fortune.jackpot.favour"), PROC_ACTIONBAR_TICKS);

            spawnProcParticlesSelf(w, attacker);

            w.playSound(null, attacker.blockPosition(),
                    w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY.get() : ModSounds.JACKPOT3.get(),
                    attacker.getSoundSource(),
                    0.5f, 1.0f);
        }
    }

    private static void spawnProcParticlesEnemy(ServerLevel w, LivingEntity target) {
        w.sendParticles(
                ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getBbHeight() * 0.60, target.getZ(),
                14,
                0.25, 0.30, 0.25,
                0.0
        );
        w.sendParticles(
                ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                10,
                0.20, 0.20, 0.20,
                0.04
        );
    }

    private static void spawnProcParticlesSelf(ServerLevel w, ServerPlayer player) {
        w.sendParticles(
                ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18,
                0.35, 0.45, 0.35,
                0.0
        );
        w.sendParticles(
                ParticleTypes.FIREWORK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                1,
                0.0, 0.0, 0.0,
                0.0
        );
    }


    /* ============================================================
       PRIMARY
       ============================================================ */

    private static final float PRIM_SELF_DAMAGE = 6.0f; // 3 hearts
    private static final int PRIM_STR_AMP = 0;          // Strength 1
    private static final int PRIM_SPEED_AMP = 1;        // Speed 2

    private static final float JACKPOT_BASE = 0.06f;        // no luck odds
    private static final float JACKPOT_BONUS_AT_MAX = 0.28f; // max luck bonus chance
    private static final float JACKPOT_MAX = 0.60f;         // hard cap

    // Jackpot rewards
    private static final int JACKPOT_REGEN_TICKS  = 40;     // regeneration time
    private static final int PRIM_BUFF_TICKS = 80; // time buffed

    // egg
    private static final int FUNNY_CHANCE = 300; // funny

    @Override
    public void activatePrimary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        // self damage
        player.hurt(ModDamageTypes.bet(w, player), PRIM_SELF_DAMAGE); // bet damage

        // If they died stop
        if (!player.isAlive()) return;

        // base buffs
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, PRIM_BUFF_TICKS, PRIM_STR_AMP, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PRIM_BUFF_TICKS, PRIM_SPEED_AMP, true, false));

        // Base FX
        w.playSound(null, player.blockPosition(),
                ModSounds.ALLIN.get(),
                player.getSoundSource(),
                0.6f, 1.0f);

        w.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.35, 0.45, 0.35, 0.0);

        // JACKPOT
        if (tryAllInJackpot(player, w)) {
            net.minecraft.sounds.SoundEvent jpSound = switch(w.random.nextInt(3)) {
                case 0 -> ModSounds.JACKPOT.get();
                case 1 -> ModSounds.JACKPOT2.get();
                default -> ModSounds.JACKPOT3.get();
            };

            if (w.random.nextInt(FUNNY_CHANCE) == 0) {
                jpSound = ModSounds.JACKPOTFUNNY.get();
            }

            w.playSound(null, player.blockPosition(),
                    jpSound,
                    player.getSoundSource(),
                    0.5f, 1.0f);

            w.sendParticles(ParticleTypes.FIREWORK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);

            w.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.1, player.getZ(),
                    60, 0.65, 0.55, 0.65, 0.0);

        }
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static boolean tryAllInJackpot(ServerPlayer player, ServerLevel w) { // attempts and applies jackpot
        FortuneState state = getState(player);
        float t = state.luck / (float) LUCK_MAX;

        float chance = JACKPOT_BASE + (JACKPOT_BONUS_AT_MAX * t);
        chance = Mth.clamp(chance, 0.0f, JACKPOT_MAX);

        if (w.random.nextFloat() >= chance) return false;

        // picks one jackpot effect
        int roll = w.random.nextInt(3);

        if (roll == 0) {
            // Strength 2 instead
            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_BOOST, PRIM_BUFF_TICKS, 1, true, false
            ));
            CooldownUI.pushActionbarOverride(player, Component.translatable("power.loopypowers.fortune.jackpot.raise"), 50);

        } else if (roll == 1) {
            // brief regeneration
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, JACKPOT_REGEN_TICKS, 0, true, false
            ));
            CooldownUI.pushActionbarOverride(player, Component.translatable("power.loopypowers.fortune.jackpot.draw_no_bet"), 50);

        } else {
            // doubled time
            int doubled = PRIM_BUFF_TICKS * 2;

            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_BOOST, doubled, PRIM_STR_AMP, true, false
            ));
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, doubled, PRIM_SPEED_AMP, true, false
            ));
            CooldownUI.pushActionbarOverride(player, Component.translatable("power.loopypowers.fortune.jackpot.double_time"), 50);
        }
        return true;
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final double DUEL_CAST_RANGE = 24.0;
    private static final double DUEL_MAX_RANGE  = 20.0;        // leash before it breaks
    private static final int    DUEL_DURATION_TICKS = 20 * 10; // 10s
    private static final float  DUEL_VS_PARTNER_MULT = 1.5f;   // +50%
    private static final float  DUEL_VS_OTHERS_MULT  = 0.5f;   // -50%

    // fx cadence
    private static final int DUEL_TETHER_EVERY_TICKS = 2;

    private static final Map<UUID, DuelInstance> ACTIVE_DUELS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> DUEL_LAST_TICK = new HashMap<>();

    private static final class DuelInstance {
        final ResourceKey<Level> worldKey;
        final UUID a;
        final UUID b;
        int ticksLeft;

        DuelInstance(ResourceKey<Level> worldKey, UUID a, UUID b, int ticksLeft) {
            this.worldKey = worldKey;
            this.a = a;
            this.b = b;
            this.ticksLeft = ticksLeft;
        }

        boolean contains(UUID u) {
            return a.equals(u) || b.equals(u);
        }
    }

    @Override
    public void activateSecondary(ServerPlayer player) {
        ServerLevel w = player.serverLevel();

        // If already dueling, you cannot cancel or overwrite it
        if (isInDuel(player)) {
            /*
            breakDuel(player.getUUID());
            w.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, player.getSoundSource(), 0.8f, 1.0f);
            CooldownUI.pushActionbarOverride(player, Component.translatable("power.loopypowers.fortune.duel_ended"), 35);
            */
            return;
        }

        player.swing(InteractionHand.MAIN_HAND, true);

        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0f).normalize();
        Vec3 end = start.add(dir.scale(DUEL_CAST_RANGE));

        // stop at blocks
        HitResult blockHit = w.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        Vec3 blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getLocation() : end;

        // find nearest living entity along the segment
        LivingEntity hitEntity = null;
        Vec3 hitPos = null;
        double bestDistSq = start.distanceToSqr(blockEnd);

        AABB searchBox = new AABB(start, blockEnd).inflate(1.2);
        List<LivingEntity> candidates = w.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity e : candidates) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.25).clip(start, blockEnd);
            if (hit.isEmpty()) continue;

            Vec3 p = hit.get();
            double d = start.distanceToSqr(p);
            if (d < bestDistSq) {
                bestDistSq = d;
                hitEntity = e;
                hitPos = p;
            }
        }

        Vec3 beamEnd = (hitPos != null) ? hitPos : blockEnd;

        // beam fx
        spawnDuelBeam(w, start, beamEnd);
        w.playSound(null, player.blockPosition(), ModSounds.RAISESTAKES.get(), player.getSoundSource(), 1.0f, 1.0f);

        if (hitEntity == null) {
            w.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, player.getSoundSource(), 0.6f, 0.6f);
            return;
        }

        // start duel
        startDuel(player, hitEntity);
    }

    private static boolean isInDuel(LivingEntity e) {
        return ACTIVE_DUELS.containsKey(e.getUUID());
    }

    private static void breakDuel(UUID member) {
        DuelInstance d = ACTIVE_DUELS.get(member);
        if (d == null) return;

        ACTIVE_DUELS.remove(d.a);
        ACTIVE_DUELS.remove(d.b);
    }

    private static void startDuel(ServerPlayer caster, LivingEntity target) {
        // break any existing duel on anyone effected
        breakDuel(caster.getUUID());
        breakDuel(target.getUUID());

        ServerLevel w = caster.serverLevel();
        DuelInstance d = new DuelInstance(w.dimension(), caster.getUUID(), target.getUUID(), DUEL_DURATION_TICKS);

        // index by both members
        ACTIVE_DUELS.put(d.a, d);
        ACTIVE_DUELS.put(d.b, d);

        w.playSound(null, caster.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                caster.getSoundSource(),
                0.9f, 1.15f);
    }

    private static void tickDuelsWorld(ServerLevel w) {
        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();

        Long last = DUEL_LAST_TICK.get(key);
        if (last != null && last == now) return;
        DUEL_LAST_TICK.put(key, now);

        if (ACTIVE_DUELS.isEmpty()) return;

        // snapshot to avoid infinite
        List<DuelInstance> snapshot = new ArrayList<>(ACTIVE_DUELS.values());
        Set<DuelInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<UUID> toBreak = new ArrayList<>();

        for (DuelInstance d : snapshot) {
            if (!seen.add(d)) continue;          // dedupe
            if (!d.worldKey.equals(key)) continue;

            d.ticksLeft--;
            if (d.ticksLeft <= 0) {
                toBreak.add(d.a);
                continue;
            }

            Entity ea = w.getEntity(d.a);
            Entity eb = w.getEntity(d.b);

            if (!(ea instanceof LivingEntity a) || !(eb instanceof LivingEntity b) || !a.isAlive() || !b.isAlive()) {
                toBreak.add(d.a);
                continue;
            }

            double maxSq = DUEL_MAX_RANGE * DUEL_MAX_RANGE;
            if (a.distanceToSqr(b) > maxSq) {
                w.playSound(null, a.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_BREAK,
                        a.getSoundSource(),
                        0.8f, 1.0f);
                toBreak.add(d.a);
                continue;
            }

            if ((now % DUEL_TETHER_EVERY_TICKS) == 0) {
                spawnDuelTether(w, a, b);
                spawnDuelAura(w, a);
                spawnDuelAura(w, b);
            }
        }

        // Apply removals after iteration - avoid crash
        for (UUID u : toBreak) {
            breakDuel(u);
        }
    }

    private static void spawnDuelBeam(ServerLevel w, Vec3 start, Vec3 end) {
        PacketDistributor.sendToPlayersNear(
                w, null,
                start.x, start.y, start.z,
                64.0D,
                new DuelBeamPayload(start, end)
        );
    }

    private static void spawnDuelTether(ServerLevel w, LivingEntity a, LivingEntity b) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                a,
                new DuelTetherPayload(a.getId(), b.getId())
        );
    }

    private static void spawnDuelAura(ServerLevel w, LivingEntity e) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                e,
                new DuelAuraPayload(e.getId())
        );
    }

    private static float getDuelMultiplier(LivingEntity victim, LivingEntity attacker) {
        DuelInstance dv = ACTIVE_DUELS.get(victim.getUUID());
        DuelInstance da = ACTIVE_DUELS.get(attacker.getUUID());

        if (dv == null && da == null) return 1.0f;

        if (dv != null) {
            return dv.contains(attacker.getUUID()) ? DUEL_VS_PARTNER_MULT : DUEL_VS_OTHERS_MULT;
        }
        return DUEL_VS_OTHERS_MULT;
    }

   /* ============================================================
   ULTIMATE
   ============================================================ */

    // Cage
    private static final int HOUSE_RADIUS = 8;
    private static final int HOUSE_WALL_LAYERS = 4;
    private static final int HOUSE_BUILD_INTERVAL_TICKS = 7;
    private static final int HOUSE_ACTIVE_TICKS = 298;
    private static final int HOUSE_CLEAR_HEIGHT = 10;
    private static final int HOUSE_MAX_RULES = 4;

    // Roof particles
    private static final int HOUSE_ROOF_FX_EVERY_TICKS = 2;
    private static final int HOUSE_ROOF_FX_PARTICLES = 28;
    private static final double HOUSE_ROOF_FX_Y_OFFSET = 1.15;

    // Block breaking
    private static final int HOUSE_BREAK_FROM_TOP_OFFSET = 2;  // y >= baseY + (HOUSE_WALL_LAYERS - 2)
    private static final int HOUSE_BREAK_SCAN_EXTRA_Y = 10;
    private static final int HOUSE_BREAK_EVERY_TICKS = 2;
    private static final int HOUSE_BREAK_MARGIN = 1;
    private static final int HOUSE_BREAK_ENCHANT_PARTICLES = 10;

    // Rules
    private static final int HOUSE_RULE_INTERVAL_TICKS = 80; // every 4s
    private static final int HOUSE_RULE_MIN_PLAYERS_TO_ANNOUNCE = 1;

    private static final float RULE_WOOLIAM_CHANCE = 0.004f; // EGG!!!

    // Roulette
    private static final float RULE_ROULETTE_DAMAGE = 14.0f;

    // Lightning Round
    private static final int RULE_LIGHTNING_EVERY_TICKS = 10;
    private static final float RULE_LIGHTNING_DAMAGE = 1.0f;

    // Hot Seat
    private static final int RULE_HOTSEAT_FUSE_TICKS = 60;
    private static final float RULE_HOTSEAT_DAMAGE = 28.0f;
    private static final int RULE_HOTSEAT_PARTICLES_EVERY_TICKS = 2;

    // Double or Nothing
    private static final float RULE_DOUBLE_MULT = 1.25f;

    // The Bouncer
    private static final float RULE_BOUNCER_DAMAGE = 8.0f;
    private static final double RULE_BOUNCER_BOUNCE_MULT = 2.6;

    // Rule effect duration scaling (all potion rules)
    private static final float HOUSE_RULE_EFFECT_MULT = 1.75f; // e.g. 1.75x longer
    private static final int   HOUSE_RULE_EFFECT_BONUS_TICKS = 40; // +2s

    // Chip Toss
    private static final float RULE_CHIP_TOSS_FRACTION = 0.70f; // 50%
    private static final double RULE_CHIP_TOSS_UP_MIN = 1.05;
    private static final double RULE_CHIP_TOSS_UP_MAX = 1.75;
    private static final double RULE_CHIP_TOSS_SIDE = 0.35;

    // Smoke Machine
    private static final int RULE_SMOKE_PARTICLES_EVERY_TICKS = 2;
    private static final int RULE_SMOKE_PARTICLES_PER_ENTITY = 8;

    // Spotlight
    private static final float RULE_SPOTLIGHT_MULT = 1.50f; // victim takes +50% dmg
    private static final int RULE_SPOTLIGHT_RETRY_EVERY_TICKS = 10;

    // Jackpot (next hit in room)
    private static final float RULE_JACKPOT_MULT = 4.0f;
    private static final int RULE_JACKPOT_FX_PARTICLES = 18;

    // Wildcards
    private static final int RULE_WILDCARDS_MIN = 2;
    private static final int RULE_WILDCARDS_MAX = 8;

    private static int ruleEffectDurationTicks() {
        int base = HOUSE_RULE_INTERVAL_TICKS + 10;
        return Mth.ceil(base * HOUSE_RULE_EFFECT_MULT) + HOUSE_RULE_EFFECT_BONUS_TICKS;
    }

    private enum HouseRule {
        LOADED_DICE,
        FREE_DRINKS,
        VIP_PASS,
        NO_RUNNING,
        NO_FIGHTING,
        DOUBLE_OR_NOTHING,
        SHUFFLE,
        ROULETTE,
        LIGHTNING_ROUND,
        HOT_SEAT,
        CHIP_TOSS,
        WILDCARDS,
        SMOKE_MACHINE,
        SPOTLIGHT,
        JACKPOT,
        CARD_COUNTER,
        WOOLIAM_INVASION,
        BOUNCER
    }

    private static final Map<UUID, HouseState> ACTIVE_HOUSES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> HOUSE_LAST_TICK = new HashMap<>();

    private static final class HouseState {
        final UUID owner;
        final ResourceKey<Level> worldKey;
        final BlockPos center;
        final int baseY;

        int buildLayer;
        int buildWait;
        int activeLeft;
        boolean built;

        final Set<BlockPos> placed = new HashSet<>();
        final Set<UUID> inside = new HashSet<>();

        // rules
        HouseRule rule = null;
        int ruleLeft = HOUSE_RULE_INTERVAL_TICKS;
        int rulesPlayed = 0;

        // hot seat
        UUID hotSeatHolder = null;
        int hotSeatFuse = 0;

        // spotlight + jackpot
        UUID spotlightTarget = null;
        boolean jackpotArmed = false;

        HouseState(UUID owner, ResourceKey<Level> worldKey, BlockPos center) {
            this.owner = owner;
            this.worldKey = worldKey;
            this.center = center;
            this.baseY = center.getY();

            this.buildLayer = 0;
            this.buildWait = HOUSE_BUILD_INTERVAL_TICKS;
            this.activeLeft = HOUSE_ACTIVE_TICKS + (HOUSE_WALL_LAYERS * HOUSE_BUILD_INTERVAL_TICKS) + 20;
            this.built = false;
            this.rulesPlayed = 0;
        }
    }

    @Override
    public void activateUltimate(ServerPlayer player) {
        ServerLevel w = player.serverLevel();
        var server = player.getServer();

        removeHouseNow(server, player.getUUID());

        BlockPos center = player.blockPosition();
        HouseState st = new HouseState(player.getUUID(), w.dimension(), center);
        ACTIVE_HOUSES.put(player.getUUID(), st);

        placeHouseFloor(w, st);
        clearHouseInteriorAbove(w, st);
        updateHouseInside(w, st);

        // fx
        w.playSound(null, center, SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.PLAYERS, 0.9f, 0.9f);

        w.sendParticles(ParticleTypes.ENCHANT,
                center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5,
                35, 0.9, 0.4, 0.9, 0.0);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    // this is called by the world in loopypowers class
    public static void tickHousesWorld(ServerLevel w) {
        long now = w.getGameTime();
        ResourceKey<Level> key = w.dimension();

        Long last = HOUSE_LAST_TICK.get(key);
        if (last != null && last == now) return;
        HOUSE_LAST_TICK.put(key, now);

        if (ACTIVE_HOUSES.isEmpty()) return;

        var server = w.getServer();

        List<Map.Entry<UUID, HouseState>> snapshot = new ArrayList<>(ACTIVE_HOUSES.entrySet());
        List<UUID> toRemove = new ArrayList<>();

        for (var entry : snapshot) {
            UUID owner = entry.getKey();
            HouseState st = entry.getValue();

            if (!st.worldKey.equals(key)) continue;

            updateHouseInside(w, st);
            enforceHousePrison(w, st); // keep trapped

            if ((now % HOUSE_ROOF_FX_EVERY_TICKS) == 0L) spawnHouseRoofFx(w, st);
            if ((now % HOUSE_BREAK_EVERY_TICKS) == 0L) enforceHouseBuildCeiling(w, st);

            // Build phase
            if (!st.built) {
                st.buildWait--;
                if (st.buildWait <= 0) {
                    placeHouseBarsLayer(w, st, st.buildLayer);
                    st.buildLayer++;
                    st.buildWait = HOUSE_BUILD_INTERVAL_TICKS;

                    if (st.buildLayer >= HOUSE_WALL_LAYERS) {
                        st.built = true;

                        w.playSound(null, st.center, SoundEvents.ANVIL_LAND,
                                SoundSource.PLAYERS, 0.75f, 1.1f);

                        w.sendParticles(ParticleTypes.POOF,
                                st.center.getX() + 0.5, st.baseY + 0.2, st.center.getZ() + 0.5,
                                16, 0.6, 0.2, 0.6, 0.04);

                        forceNewRule(w, st);
                    } else {
                        w.playSound(null, st.center, SoundEvents.CHAIN_PLACE,
                                SoundSource.PLAYERS, 0.45f, 1.25f);
                    }
                }
                continue;
            }

            // active phase
            st.activeLeft--;

            // rules tick + rotate
            tickHouseRules(w, st);

            if (st.activeLeft <= 0) toRemove.add(owner);
        }

        for (UUID owner : toRemove) {
            removeHouseNow(server, owner);
        }
    }

    private static void tickHouseRules(ServerLevel w, HouseState st) {
        if (st.rule == null) {
            if (st.rulesPlayed < HOUSE_MAX_RULES) {
                forceNewRule(w, st);
            }
            return;
        }

        // per-rule ticking behavior
        if (st.rule == HouseRule.LIGHTNING_ROUND) {
            if ((w.getGameTime() % RULE_LIGHTNING_EVERY_TICKS) == 0L) {
                Entity owner = w.getEntity(st.owner);
                for (LivingEntity e : getHouseLiving(w, st)) {
                    w.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                            6, 0.25, 0.25, 0.25, 0.0);
                    e.hurt(ModDamageTypes.house(w, owner), RULE_LIGHTNING_DAMAGE); // house deals damage
                }
            }
        } else if (st.rule == HouseRule.HOT_SEAT) {
            tickHotSeat(w, st);
        } else if (st.rule == HouseRule.SMOKE_MACHINE) {
            if ((w.getGameTime() % RULE_SMOKE_PARTICLES_EVERY_TICKS) == 0L) {
                spawnSmokeMachineFx(w, st);
            }
        } else if (st.rule == HouseRule.SPOTLIGHT) {
            if ((w.getGameTime() % RULE_SPOTLIGHT_RETRY_EVERY_TICKS) == 0L) {
                tickSpotlight(w, st);
            }
        }

        st.ruleLeft--;
        if (st.ruleLeft <= 0) {
            forceNewRule(w, st);
        }
    }

    private static void forceNewRule(ServerLevel w, HouseState st) {
        // clear last rules persistent state
        if (st.rule == HouseRule.HOT_SEAT) {
            st.hotSeatHolder = null;
            st.hotSeatFuse = 0;
        }
        if (st.rule == HouseRule.SPOTLIGHT) {
            st.spotlightTarget = null;
        }
        if (st.rule == HouseRule.JACKPOT) {
            st.jackpotArmed = false;
        }

        if (st.rulesPlayed >= HOUSE_MAX_RULES) {
            st.rule = null;
            return;
        }

        st.rulesPlayed++;

        HouseRule next = rollRule(w, st.rule);
        st.rule = next;
        st.ruleLeft = HOUSE_RULE_INTERVAL_TICKS;

        announceRule(w, st, next);

        switch (next) { // rules that just apply potion effects
            case LOADED_DICE -> applyToOwner(w, st,
                    new MobEffectInstance(MobEffects.DAMAGE_BOOST, ruleEffectDurationTicks(), 1, true, false));

            case VIP_PASS -> applyToOwner(w, st,
                    new MobEffectInstance(MobEffects.REGENERATION, ruleEffectDurationTicks(), 0, true, false));

            case FREE_DRINKS -> applyToAll(w, st,
                    new MobEffectInstance(MobEffects.REGENERATION, ruleEffectDurationTicks(), 0, true, false));

            case NO_RUNNING -> applyToAll(w, st,
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ruleEffectDurationTicks(), 1, true, false));

            case NO_FIGHTING -> applyToAll(w, st,
                    new MobEffectInstance(MobEffects.WEAKNESS, ruleEffectDurationTicks(), 1, true, false));

            // other

            case DOUBLE_OR_NOTHING -> w.sendParticles(ParticleTypes.ENCHANT,
                    st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                    18, 0.9, 0.35, 0.9, 0.0);

            case BOUNCER -> {
                w.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                        30, 1.0, 0.5, 1.0, 0.1);
                w.playSound(null, st.center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.8f);
            }

            case SHUFFLE -> doShuffle(w, st);

            case ROULETTE -> doRoulette(w, st);

            case LIGHTNING_ROUND -> w.playSound(null, st.center, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.PLAYERS, 0.6f, 1.4f);

            case HOT_SEAT -> startHotSeat(w, st);

            case CHIP_TOSS -> doChipToss(w, st);

            case WILDCARDS -> spawnWildcards(w, st);

            case SMOKE_MACHINE -> {
                applyToAll(w, st, new MobEffectInstance(MobEffects.BLINDNESS, ruleEffectDurationTicks(), 0, true, false));
                w.sendParticles(ParticleTypes.LARGE_SMOKE,
                        st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                        25, 1.2, 0.6, 1.2, 0.02);
            }

            case SPOTLIGHT -> startSpotlight(w, st);

            case JACKPOT -> armJackpot(w, st);

            case CARD_COUNTER -> doCardCounter(w, st);

            case WOOLIAM_INVASION -> spawnWooliamInvasion(w, st);
        }
    }

    private static HouseRule rollRule(ServerLevel w, HouseRule current) {
        if (w.random.nextFloat() < RULE_WOOLIAM_CHANCE) {
            return HouseRule.WOOLIAM_INVASION;
        }

        HouseRule[] all = HouseRule.values();
        HouseRule pick;
        int guard = 0;
        do {
            pick = all[w.random.nextInt(all.length)];
            guard++;
        } while ((pick == current || pick == HouseRule.WOOLIAM_INVASION) && guard < 10);

        return pick;
    }

    private static void announceRule(ServerLevel w, HouseState st, HouseRule rule) {
        String ruleKey = rule.name().toLowerCase();

        Component name = Component.translatable("power.loopypowers.fortune.rule." + ruleKey + ".name");
        Component desc = Component.translatable("power.loopypowers.fortune.rule." + ruleKey + ".desc");

        Component msg = Component.translatable("power.loopypowers.fortune.house_rule.format", name, desc);

        int playerCount = 0;
        for (UUID u : st.inside) {
            Entity e = w.getEntity(u);
            if (e instanceof ServerPlayer sp) {
                playerCount++;
                sp.sendSystemMessage(msg);
            }
        }

        if (playerCount >= HOUSE_RULE_MIN_PLAYERS_TO_ANNOUNCE) {
            w.playSound(null, st.center, ModSounds.NEWRULE.get(),
                    SoundSource.PLAYERS, 0.5f, 0.9f);
        }
    }

    // helper methods for effects
    private static void applyToOwner(ServerLevel w, HouseState st, MobEffectInstance fx) {
        Entity e = w.getEntity(st.owner);
        if (e instanceof LivingEntity le && le.isAlive()) le.addEffect(fx);
    }

    private static void applyToAll(ServerLevel w, HouseState st, MobEffectInstance fx) {
        for (LivingEntity e : getHouseLiving(w, st)) e.addEffect(fx);
    }

    private static List<LivingEntity> getHouseLiving(ServerLevel w, HouseState st) {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID u : st.inside) {
            Entity e = w.getEntity(u);
            if (e instanceof LivingEntity le && le.isAlive()) out.add(le);
        }
        return out;
    }

    /* rule methods */

    private static void doShuffle(ServerLevel w, HouseState st) { // swaps around entities
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.size() <= 1) return;

        List<Vec3> positions = new ArrayList<>(ents.size());
        List<Float> yaws = new ArrayList<>(ents.size());
        List<Float> pitches = new ArrayList<>(ents.size());

        for (LivingEntity e : ents) {
            positions.add(e.position());
            yaws.add(e.getYRot());
            pitches.add(e.getXRot());
        }

        int n = ents.size();
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = w.random.nextInt(i + 1);
            int tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp;
        }

        for (int i = 0; i < n; i++) {
            LivingEntity e = ents.get(i);
            Vec3 p = positions.get(idx[i]);
            float yaw = yaws.get(idx[i]);
            float pitch = pitches.get(idx[i]);
            teleportEntity(w, e, p, yaw, pitch);

            w.sendParticles(ParticleTypes.POOF,
                    p.x, p.y + e.getBbHeight() * 0.5, p.z,
                    8, 0.25, 0.25, 0.25, 0.02);
        }

        w.playSound(null, st.center, SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    private static void doRoulette(ServerLevel w, HouseState st) { // does not select owner
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.isEmpty()) return;

        ents.removeIf(e -> e.getUUID().equals(st.owner));
        if (ents.isEmpty()) return;

        LivingEntity pick = ents.get(w.random.nextInt(ents.size()));

        w.sendParticles(ParticleTypes.CRIT,
                pick.getX(), pick.getY() + pick.getBbHeight() * 0.6, pick.getZ(),
                16, 0.35, 0.35, 0.35, 0.08);

        Entity owner = w.getEntity(st.owner);
        pick.hurt(ModDamageTypes.house(w, owner), RULE_ROULETTE_DAMAGE); // house deals damage

        if (pick instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("power.loopypowers.fortune.roulette_chosen"));
        }

        w.playSound(null, pick.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.9f, 0.7f);
    }

    // hot potato
    private static void startHotSeat(ServerLevel w, HouseState st) {
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.isEmpty()) return;

        LivingEntity pick = ents.get(w.random.nextInt(ents.size()));
        st.hotSeatHolder = pick.getUUID();
        st.hotSeatFuse = RULE_HOTSEAT_FUSE_TICKS;

        w.sendParticles(ParticleTypes.ENCHANT,
                pick.getX(), pick.getY() + pick.getBbHeight() * 0.7, pick.getZ(),
                18, 0.35, 0.35, 0.35, 0.0);
    }

    private static void tickHotSeat(ServerLevel w, HouseState st) {
        if (st.hotSeatHolder == null) {
            startHotSeat(w, st);
            return;
        }

        Entity e = w.getEntity(st.hotSeatHolder);
        if (!(e instanceof LivingEntity holder) || !holder.isAlive() || !st.inside.contains(st.hotSeatHolder)) {
            st.hotSeatHolder = null;
            st.hotSeatFuse = 0;
            startHotSeat(w, st);
            return;
        }

        if ((w.getGameTime() % RULE_HOTSEAT_PARTICLES_EVERY_TICKS) == 0L) {
            w.sendParticles(ParticleTypes.ENCHANT,
                    holder.getX(), holder.getY() + holder.getBbHeight() * 0.6, holder.getZ(),
                    10, 0.25, 0.35, 0.25, 0.0);
            w.sendParticles(ParticleTypes.CRIT,
                    holder.getX(), holder.getY() + holder.getBbHeight() * 0.6, holder.getZ(),
                    2, 0.15, 0.15, 0.15, 0.02);
        }

        st.hotSeatFuse--;
        if (st.hotSeatFuse > 0) return;

        w.sendParticles(ParticleTypes.CLOUD,
                holder.getX(), holder.getY() + holder.getBbHeight() * 0.5, holder.getZ(),
                18, 0.35, 0.25, 0.35, 0.03);

        Entity owner = w.getEntity(st.owner);
        holder.hurt(ModDamageTypes.house(w, owner), RULE_HOTSEAT_DAMAGE); // house deals damage

        st.hotSeatHolder = null;
        st.hotSeatFuse = 0;
        startHotSeat(w, st);

        w.playSound(null, st.center, SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS, 0.7f, 1.25f);
    }

    // if current holder does damage pass the hot potato
    private static void tryPassHotSeatOnHit(ServerLevel w, HouseState st, LivingEntity attacker, LivingEntity victim) {
        if (st.rule != HouseRule.HOT_SEAT) return;
        if (st.hotSeatHolder == null) return;
        if (!attacker.getUUID().equals(st.hotSeatHolder)) return;

        LivingEntity newHolder = victim;

        if (!st.inside.contains(victim.getUUID())) {
            List<LivingEntity> options = getHouseLiving(w, st);
            options.removeIf(ent -> ent.getUUID().equals(attacker.getUUID()));
            if (options.isEmpty()) return;
            newHolder = options.get(w.random.nextInt(options.size()));
        }

        st.hotSeatHolder = newHolder.getUUID();
        st.hotSeatFuse = RULE_HOTSEAT_FUSE_TICKS;

        w.sendParticles(ParticleTypes.ENCHANT,
                attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
                10, 0.25, 0.25, 0.25, 0.0);
        w.sendParticles(ParticleTypes.ENCHANT,
                newHolder.getX(), newHolder.getY() + newHolder.getBbHeight() * 0.6, newHolder.getZ(),
                14, 0.25, 0.25, 0.25, 0.0);

        w.playSound(null, newHolder.blockPosition(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.6f, 1.35f);
    }

    // throw random entities in air, ignores owner
    private static void doChipToss(ServerLevel w, HouseState st) {
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.isEmpty()) return;

        ents.removeIf(e -> e.getUUID().equals(st.owner));
        if (ents.isEmpty()) return;

        Collections.shuffle(ents, new Random(w.random.nextLong()));
        int count = Math.max(1, Math.round(ents.size() * RULE_CHIP_TOSS_FRACTION));

        for (int i = 0; i < count; i++) {
            LivingEntity e = ents.get(i);

            double up = RULE_CHIP_TOSS_UP_MIN + w.random.nextDouble() * (RULE_CHIP_TOSS_UP_MAX - RULE_CHIP_TOSS_UP_MIN);
            double sx = (w.random.nextDouble() * 2 - 1) * RULE_CHIP_TOSS_SIDE;
            double sz = (w.random.nextDouble() * 2 - 1) * RULE_CHIP_TOSS_SIDE;

            e.setDeltaMovement(e.getDeltaMovement().add(sx, up, sz));
            e.hasImpulse = true;
            e.hurtMarked  = true; // sync velocity to all tracking clients, not just ServerPlayers

            if (e instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }

            w.sendParticles(ParticleTypes.CRIT,
                    e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                    10, 0.25, 0.35, 0.25, 0.10);
        }

        w.sendParticles(ParticleTypes.ENCHANT,
                st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                18, 1.0, 0.35, 1.0, 0.0);
    }

    // spawns random entities
    private static void spawnWildcards(ServerLevel w, HouseState st) {
        int n = RULE_WILDCARDS_MIN + w.random.nextInt(RULE_WILDCARDS_MAX - RULE_WILDCARDS_MIN + 1);

        EntityType<?>[] pool = new EntityType<?>[] { // entities that can spawn
                EntityType.ZOMBIE,
                EntityType.SKELETON,
                EntityType.SPIDER,
                EntityType.HUSK,
                EntityType.STRAY
        };

        for (int i = 0; i < n; i++) {
            EntityType<?> type = pool[w.random.nextInt(pool.length)];
            Entity created = type.create(w);
            if (!(created instanceof LivingEntity mob)) continue;

            BlockPos p = randomInsidePos(w, st, 2);
            mob.moveTo(
                    p.getX() + 0.5, st.baseY + 0.1, p.getZ() + 0.5,
                    w.random.nextFloat() * 360f, 0f
            );

            w.addFreshEntity(mob);

            w.sendParticles(ParticleTypes.POOF,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    10, 0.25, 0.25, 0.25, 0.02);
        }
    }

    // EGG - SEND THE WOOLIAMS
    private static void spawnWooliamInvasion(ServerLevel w, HouseState st) {
        // 20 woolliam
        for (int i = 0; i < 20; i++) {
            Sheep sheep = EntityType.SHEEP.create(w);
            if (sheep != null) {
                BlockPos p = randomInsidePos(w, st, 1);
                sheep.moveTo(p.getX() + 0.5, st.baseY + 0.1, p.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
                sheep.setCustomName(Component.translatable("power.loopypowers.fortune.wooliam"));
                w.addFreshEntity(sheep);
                w.sendParticles(ParticleTypes.POOF, sheep.getX(), sheep.getY() + 0.5, sheep.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
            }
        }
        // 1 hamuel
        Pig pig = EntityType.PIG.create(w);
        if (pig != null) {
            BlockPos p = randomInsidePos(w, st, 1);
            pig.moveTo(p.getX() + 0.5, st.baseY + 0.1, p.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
            pig.setCustomName(Component.translatable("power.loopypowers.fortune.hamuel"));
            w.addFreshEntity(pig);
            w.sendParticles(ParticleTypes.POOF, pig.getX(), pig.getY() + 0.5, pig.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
        }
        w.playSound(null, st.center, SoundEvents.SHEEP_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.0f);
    }

    // gets random spot in area, for mob spawning
    private static BlockPos randomInsidePos(ServerLevel w, HouseState st, int margin) {
        int cx = st.center.getX();
        int cz = st.center.getZ();

        int r = Math.max(1, HOUSE_RADIUS - margin);
        int dx = w.random.nextInt(r * 2 + 1) - r;
        int dz = w.random.nextInt(r * 2 + 1) - r;

        return new BlockPos(cx + dx, st.baseY, cz + dz);
    }

    // smoke particles
    private static void spawnSmokeMachineFx(ServerLevel w, HouseState st) {
        for (LivingEntity e : getHouseLiving(w, st)) {
            w.sendParticles(ParticleTypes.SMOKE,
                    e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                    RULE_SMOKE_PARTICLES_PER_ENTITY,
                    0.45, 0.35, 0.45,
                    0.01);
        }
    }

    // pick a target and give them effects
    private static void startSpotlight(ServerLevel w, HouseState st) {
        LivingEntity target = pickSpotlightTarget(w, st);
        if (target == null) {
            st.spotlightTarget = null;
            return;
        }

        st.spotlightTarget = target.getUUID();
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ruleEffectDurationTicks(), 0, true, false));

        w.sendParticles(ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                18, 0.35, 0.35, 0.35, 0.0);
    }

    private static void tickSpotlight(ServerLevel w, HouseState st) {
        if (st.spotlightTarget == null) {
            startSpotlight(w, st); // selects new target if target dead
            return;
        }

        Entity e = w.getEntity(st.spotlightTarget);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || !st.inside.contains(st.spotlightTarget)) {
            st.spotlightTarget = null;
            startSpotlight(w, st);
            return;
        }

        w.sendParticles(ParticleTypes.CRIT,
                le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(),
                2, 0.25, 0.25, 0.25, 0.02);
    }

    private static LivingEntity pickSpotlightTarget(ServerLevel w, HouseState st) {
        List<LivingEntity> ents = getHouseLiving(w, st);
        ents.removeIf(e -> e.getUUID().equals(st.owner)); // cannot be caster
        if (ents.isEmpty()) return null;
        return ents.get(w.random.nextInt(ents.size()));
    }

    // start jackpot
    private static void armJackpot(ServerLevel w, HouseState st) {
        st.jackpotArmed = true;
        w.sendParticles(ParticleTypes.ENCHANT,
                st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                22, 0.8, 0.35, 0.8, 0.0);
    }

    // set passive luck to max
    private static void doCardCounter(ServerLevel w, HouseState st) {
        Entity owner = w.getEntity(st.owner);
        if (owner instanceof ServerPlayer sp && sp.isAlive()) {
            FortuneState state = getState(sp);
            state.luck = LUCK_MAX;
            state.luckDecayTicks = LUCK_DECAY_DELAY_TICKS;

            w.sendParticles(ParticleTypes.ENCHANT,
                    sp.getX(), sp.getY() + 1.0, sp.getZ(),
                    22, 0.45, 0.55, 0.45, 0.0);
        }
    }

    // damage hook: decides what rule applies to the damage event
    public static boolean tryAdjustFortuneDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0) return false;

        Entity atkEnt = source.getEntity();
        LivingEntity attacker = (atkEnt instanceof LivingEntity le) ? le : null;

        // recursion guard via global hashset
        if (DAMAGE_GUARDS.contains(victim.getUUID())) return false;
        if (attacker != null && DAMAGE_GUARDS.contains(attacker.getUUID())) return false;

        if (!(victim.level() instanceof ServerLevel w)) return false;

        // find house relevant to attacker
        HouseState hs = null;
        if (attacker != null) hs = findHouseForEntity(w, attacker.getUUID());
        if (hs == null) hs = findHouseForEntity(w, victim.getUUID());

        // pass hot potato
        if (hs != null && attacker != null) {
            tryPassHotSeatOnHit(w, hs, attacker, victim);
        }

        float mult = 1.0f;
        DamageSource finalSource = source;

        // double or nothing damage boost
        if (hs != null && hs.rule == HouseRule.DOUBLE_OR_NOTHING) {
            boolean aIn = (attacker != null && hs.inside.contains(attacker.getUUID()));
            boolean vIn = hs.inside.contains(victim.getUUID());
            if (aIn || vIn) mult *= RULE_DOUBLE_MULT;
        }

        // Spotlight
        if (hs != null && hs.rule == HouseRule.SPOTLIGHT && hs.spotlightTarget != null) {
            if (victim.getUUID().equals(hs.spotlightTarget)) {
                mult *= RULE_SPOTLIGHT_MULT;
            }
        }

        // Jackpot - only applied once
        if (hs != null && hs.rule == HouseRule.JACKPOT && hs.jackpotArmed) {
            boolean aIn = (attacker != null && hs.inside.contains(attacker.getUUID()));
            boolean vIn = hs.inside.contains(victim.getUUID());
            if (attacker != null && aIn && vIn) {
                mult *= RULE_JACKPOT_MULT;
                hs.jackpotArmed = false;
                finalSource = ModDamageTypes.house(w, attacker); // uses house damage type for jackpot

                w.sendParticles(ParticleTypes.FIREWORK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        1, 0, 0, 0, 0);
                w.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        RULE_JACKPOT_FX_PARTICLES, 0.35, 0.35, 0.35, 0.12);

                net.minecraft.sounds.SoundEvent jpSound = w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY.get() : ModSounds.JACKPOT.get();
                w.playSound(null, victim.blockPosition(), jpSound, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        // Duel multiplier - not from ultimate
        if (attacker != null) {
            mult *= getDuelMultiplier(victim, attacker);
        }

        if (Math.abs(mult - 1.0f) < 1.0e-4f && finalSource == source) return false;

        float newAmount = amount * mult;

        DAMAGE_GUARDS.add(victim.getUUID());
        if (attacker != null) DAMAGE_GUARDS.add(attacker.getUUID());
        try {
            victim.hurt(finalSource, newAmount);
        } finally {
            DAMAGE_GUARDS.remove(victim.getUUID());
            if (attacker != null) DAMAGE_GUARDS.remove(attacker.getUUID());
        }
        return true; // cancel original damage in hook
    }

    private static HouseState findHouseForEntity(ServerLevel w, UUID u) {
        ResourceKey<Level> key = w.dimension();
        for (HouseState st : ACTIVE_HOUSES.values()) {
            if (!st.worldKey.equals(key)) continue;
            if (st.inside.contains(u)) return st;
        }
        return null;
    }

    /* cage building etc */

    private static void placeHouseFloor(ServerLevel w, HouseState st) {
        BlockState floor = ModBlocks.CASINO_FLOOR.get().defaultBlockState();
        if (floor.hasProperty(BlockStateProperties.WATERLOGGED)) floor = floor.setValue(BlockStateProperties.WATERLOGGED, false);

        int cx = st.center.getX();
        int cz = st.center.getZ();
        int yFloor = st.baseY - 1;

        for (int dx = -HOUSE_RADIUS; dx <= HOUSE_RADIUS; dx++) {
            for (int dz = -HOUSE_RADIUS; dz <= HOUSE_RADIUS; dz++) {
                setBlockForced(w, new BlockPos(cx + dx, yFloor, cz + dz), floor, st);
            }
        }
    }

    private static void clearHouseInteriorAbove(ServerLevel w, HouseState st) {
        int cx = st.center.getX();
        int cz = st.center.getZ();
        int inner = Math.max(0, HOUSE_RADIUS - 1);

        for (int dy = 0; dy < HOUSE_CLEAR_HEIGHT; dy++) {
            int y = st.baseY + dy;
            for (int dx = -inner; dx <= inner; dx++) {
                for (int dz = -inner; dz <= inner; dz++) {
                    clearToAirIfBreakable(w, new BlockPos(cx + dx, y, cz + dz));
                }
            }
        }
    }

    private static void placeHouseBarsLayer(ServerLevel w, HouseState st, int layer) {
        BlockState bars = ModBlocks.CASINO_BARS.get().defaultBlockState();
        if (bars.hasProperty(BlockStateProperties.WATERLOGGED)) bars = bars.setValue(BlockStateProperties.WATERLOGGED, false);

        int cx = st.center.getX();
        int cz = st.center.getZ();
        int y  = st.baseY + layer;

        List<BlockPos> placedThisLayer = new ArrayList<>();

        for (int dx = -HOUSE_RADIUS; dx <= HOUSE_RADIUS; dx++) {
            for (int dz = -HOUSE_RADIUS; dz <= HOUSE_RADIUS; dz++) {
                boolean edge = (Math.abs(dx) == HOUSE_RADIUS) || (Math.abs(dz) == HOUSE_RADIUS);
                if (!edge) continue;

                BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                clearToAirIfBreakable(w, p);
                setBlockForced(w, p, bars, st);
                placedThisLayer.add(p);
            }
        }

        for (BlockPos p : placedThisLayer) {
            BlockState cur = w.getBlockState(p);
            if (!cur.is(ModBlocks.CASINO_BARS.get())) continue;

            BlockState fixed = computeConnectedBarsState(w, p, cur);
            if (!fixed.equals(cur)) w.setBlock(p, fixed, 3);
        }
    }

    private static BlockState computeConnectedBarsState(ServerLevel w, BlockPos pos, BlockState base) { // builds the bars so they don't appear in + shape
        BlockState s = base;

        if (s.hasProperty(BlockStateProperties.NORTH)) s = s.setValue(BlockStateProperties.NORTH, shouldBarsConnect(w, pos, Direction.NORTH));
        if (s.hasProperty(BlockStateProperties.EAST))  s = s.setValue(BlockStateProperties.EAST,  shouldBarsConnect(w, pos, Direction.EAST));
        if (s.hasProperty(BlockStateProperties.SOUTH)) s = s.setValue(BlockStateProperties.SOUTH, shouldBarsConnect(w, pos, Direction.SOUTH));
        if (s.hasProperty(BlockStateProperties.WEST))  s = s.setValue(BlockStateProperties.WEST,  shouldBarsConnect(w, pos, Direction.WEST));

        if (s.hasProperty(BlockStateProperties.WATERLOGGED)) s = s.setValue(BlockStateProperties.WATERLOGGED, false);
        return s;
    }

    private static boolean shouldBarsConnect(ServerLevel w, BlockPos pos, Direction dir) { // this sucks
        BlockPos np = pos.relative(dir);
        BlockState nb = w.getBlockState(np);

        if (nb.is(ModBlocks.CASINO_BARS.get())) return true;
        return nb.isFaceSturdy(w, np, dir.getOpposite());
    }

    private static void enforceHouseBuildCeiling(ServerLevel w, HouseState st) { // breaks block n-2 from wall height
        int cx = st.center.getX();
        int cz = st.center.getZ();

        int breakFromY = st.baseY + (HOUSE_WALL_LAYERS - HOUSE_BREAK_FROM_TOP_OFFSET);
        int yMax = breakFromY + HOUSE_BREAK_SCAN_EXTRA_Y;

        int r = HOUSE_RADIUS + HOUSE_BREAK_MARGIN;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                for (int y = breakFromY; y <= yMax; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (w.isOutsideBuildHeight(p)) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    BlockState bs = w.getBlockState(p);
                    if (bs.isAir()) continue;

                    if (bs.is(ModBlocks.CASINO_BARS.get()) || bs.is(ModBlocks.CASINO_FLOOR.get())) continue;

                    float hardness = bs.getDestroySpeed(w, p);
                    if (hardness < 0) continue;

                    w.sendParticles(ParticleTypes.ENCHANT,
                            p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5,
                            HOUSE_BREAK_ENCHANT_PARTICLES,
                            0.25, 0.25, 0.25, 0.0);

                    w.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(bs));
                    w.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void updateHouseInside(ServerLevel w, HouseState st) { // maintain members
        int cx = st.center.getX();
        int cz = st.center.getZ();

        int yMin = st.baseY - 1;
        int yMax = st.baseY + HOUSE_WALL_LAYERS + 12;

        AABB box = new AABB(
                cx - HOUSE_RADIUS, yMin, cz - HOUSE_RADIUS,
                cx + HOUSE_RADIUS + 1, yMax, cz + HOUSE_RADIUS + 1
        );

        List<LivingEntity> insideNow = w.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);

        for (LivingEntity ent : insideNow) {
            BlockPos p = ent.blockPosition();
            int dx = p.getX() - cx;
            int dz = p.getZ() - cz;

            if (Math.abs(dx) <= HOUSE_RADIUS && Math.abs(dz) <= HOUSE_RADIUS) {
                // If they aren't on the list yet, add them. - list not cleared now
                if (!st.inside.contains(ent.getUUID())) {
                    st.inside.add(ent.getUUID());
                    w.sendParticles(ParticleTypes.ENCHANT,
                            ent.getX(), ent.getY() + ent.getBbHeight() * 0.6, ent.getZ(),
                            8, 0.25, 0.25, 0.25, 0.0);
                }
            }
        }
    }

    private static void spawnHouseRoofFx(ServerLevel w, HouseState st) {
        int cx = st.center.getX();
        int cz = st.center.getZ();

        double y = st.baseY + (HOUSE_WALL_LAYERS - 1) + HOUSE_ROOF_FX_Y_OFFSET;

        for (int i = 0; i < HOUSE_ROOF_FX_PARTICLES; i++) {
            double x = cx + 0.5 + (w.random.nextDouble() * 2 - 1) * HOUSE_RADIUS;
            double z = cz + 0.5 + (w.random.nextDouble() * 2 - 1) * HOUSE_RADIUS;
            w.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private static void teleportEntity(ServerLevel w, Entity e, Vec3 pos, float yaw, float pitch) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(w, pos.x, pos.y, pos.z, java.util.Collections.emptySet(), yaw, pitch);
            // FIX: Sync velocity to 0 after teleporting inside the cage
            sp.setDeltaMovement(Vec3.ZERO);
            sp.hasImpulse = true;
            sp.hurtMarked = true;
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        } else {
            e.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
            e.setDeltaMovement(Vec3.ZERO);
            e.hasImpulse = true;
            e.hurtMarked  = true; // sync zeroed velocity to tracking clients
        }
    }

    private static void removeHouseNow(net.minecraft.server.MinecraftServer server, UUID owner) {
        HouseState st = ACTIVE_HOUSES.remove(owner);
        if (st == null) return;

        ServerLevel w = server.getLevel(st.worldKey);
        if (w == null) return;

        st.inside.clear();

        for (BlockPos p : st.placed) {
            BlockState cur = w.getBlockState(p);
            if (cur.is(ModBlocks.CASINO_BARS.get()) || cur.is(ModBlocks.CASINO_FLOOR.get())) {
                w.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        st.placed.clear();

        w.playSound(null, st.center, SoundEvents.CHAIN_BREAK,
                SoundSource.PLAYERS, 0.85f, 1.0f);

        w.sendParticles(ParticleTypes.CLOUD,
                st.center.getX() + 0.5, st.baseY + 1.0, st.center.getZ() + 0.5,
                40, 0.9, 0.45, 0.9, 0.05);
    }

    private static void setBlockForced(ServerLevel w, BlockPos pos, BlockState state, HouseState st) {
        if (w.isOutsideBuildHeight(pos)) return;
        if (w.getBlockEntity(pos) != null) return;

        BlockState existing = w.getBlockState(pos);
        float hardness = existing.getDestroySpeed(w, pos);
        if (hardness < 0) return; // unbreakable

        BlockState place = state;
        if (place.hasProperty(BlockStateProperties.WATERLOGGED)) place = place.setValue(BlockStateProperties.WATERLOGGED, false);

        w.setBlock(pos, place, 3);
        st.placed.add(pos);
    }

    private static void clearToAirIfBreakable(ServerLevel w, BlockPos pos) {
        if (w.isOutsideBuildHeight(pos)) return;
        if (w.getBlockEntity(pos) != null) return;

        BlockState existing = w.getBlockState(pos);
        if (existing.isAir()) return;

        // clear all waterlogged stuff
        if (!existing.getFluidState().isEmpty()) {
            w.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }

        float hardness = existing.getDestroySpeed(w, pos);
        if (hardness < 0) return; // unbreakable

        w.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void enforceHousePrison(ServerLevel w, HouseState st) {
        Vec3 center = new Vec3(st.center.getX() + 0.5, st.baseY + 1.0, st.center.getZ() + 0.5);

        boolean isBouncer = (st.rule == HouseRule.BOUNCER);

        Iterator<UUID> it = st.inside.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity e = w.getEntity(uuid);

            // If they died, disconnected, or changed dimensions, let them go!
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }

            double dx = le.getX() - center.x;
            double dz = le.getZ() - center.z;

            // Square hitbox check instead of circular
            double maxDist = Math.max(Math.abs(dx), Math.abs(dz));

            // keep them in
            if (maxDist > (HOUSE_RADIUS - 0.8) && maxDist <= (HOUSE_RADIUS + 2.0)) {

                double bounceMult = isBouncer ? 0.6 * RULE_BOUNCER_BOUNCE_MULT : 0.6;
                Vec3 push = center.subtract(le.position()).normalize().scale(bounceMult);

                le.setDeltaMovement(le.getDeltaMovement().add(push.x, 0.2, push.z));
                le.hasImpulse = true;
                le.hurtMarked  = true; // sync velocity to all tracking clients, not just ServerPlayers

                if (le instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }

                // feedback
                if (w.getGameTime() % 5 == 0) {
                    w.playSound(null, le.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 0.5f);
                    w.sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + 1.0, le.getZ(), isBouncer ? 25 : 10, 0.2, 0.4, 0.2, isBouncer ? 0.15 : 0.05);

                    // The cage damages people who touch it during The Bouncer
                    if (isBouncer) {
                        Entity owner = w.getEntity(st.owner);
                        le.hurt(ModDamageTypes.house(w, owner), RULE_BOUNCER_DAMAGE);
                    }
                }
            }
            // yank if too far central
            else if (maxDist > (HOUSE_RADIUS + 2.0) || le.getY() > st.baseY + HOUSE_WALL_LAYERS + 2 || le.getY() < st.baseY - 1) {
                teleportEntity(w, le, center, le.getYRot(), le.getXRot());
                w.playSound(null, le.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
                w.sendParticles(ParticleTypes.ENCHANTED_HIT, le.getX(), le.getY() + 1.0, le.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
            }
        }
    }

    // OTHER HELPERS
    public static boolean cleanseDuel(LivingEntity target) {
        if (ACTIVE_DUELS.containsKey(target.getUUID())) {
            breakDuel(target.getUUID());
            return true;
        }
        return false;
    }

    // COOLDOWNS
    @Override public String getName() { return Component.translatable("power.loopypowers.fortune.name").getString(); }
    @Override public String getPrimaryName() { return Component.translatable("power.loopypowers.fortune.primary_name").getString(); }
    @Override public String getSecondaryName() { return Component.translatable("power.loopypowers.fortune.secondary_name").getString(); }
    @Override public String getUltimateName() { return Component.translatable("power.loopypowers.fortune.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs() { return 26_000; }
    @Override public long getSecondaryCooldownMs() { return 48_000; }
    @Override public long getUltimateCooldownMs() { return 540_000; }

    @Override
    public String getOverviewDescription() {
        return Component.translatable("power.loopypowers.fortune.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Component.translatable("power.loopypowers.fortune.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Component.translatable("power.loopypowers.fortune.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Component.translatable("power.loopypowers.fortune.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Component.translatable("power.loopypowers.fortune.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Component.translatable("power.loopypowers.fortune.description.ultimate").getString();
    }
}