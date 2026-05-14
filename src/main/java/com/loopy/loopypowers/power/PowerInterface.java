package com.loopy.loopypowers.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

public interface PowerInterface {

    // Called once when the player gets the power
    void onAssign(ServerPlayer player);

    // Called when the player loses the power (switches classes)
    default void onRemove(ServerPlayer player) {}

    // Called when the player dies
    default void onDeath(ServerPlayer player) {}

    // Called every tick (20 times per second)
    void onTick(ServerPlayer player);

    // try wrappers for abilities that can fail.
    default boolean tryActivatePrimary(ServerPlayer player) {
        activatePrimary(player);
        return true;
    }

    default boolean tryActivateSecondary(ServerPlayer player) {
        activateSecondary(player);
        return true;
    }

    default boolean tryActivateUltimate(ServerPlayer player) {
        activateUltimate(player);
        return true;
    }

    // Active abilities
    void activatePrimary(ServerPlayer player);
    void activateSecondary(ServerPlayer player);
    long getPrimaryCooldownMs();
    long getSecondaryCooldownMs();

    // Ultimate
    void activateUltimate(ServerPlayer player);
    long getUltimateCooldownMs();

    //Ability Names
    default String getPassiveName() { return "Passive"; }
    default String getPrimaryName() { return "Primary"; }
    default String getSecondaryName() { return "Secondary"; }
    default String getUltimateName() { return "Ultimate"; }

    // passive on hit effects (Attacker side)
    default void onHit(ServerPlayer attacker, LivingEntity target) {
        // default = nothing
    }

    // when dealing damage
    default boolean onAttack(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
        return true;
    }

    // defensive damage hook
    default boolean onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        return true;
    }

    String getName();

    // HELP / DESCRIPTIONS - for help command
    default String getOverviewDescription() {
        return "No overview description set yet.";
    }

    default String getPassiveDescription() {
        return "No passive description set yet.";
    }

    default String getPrimaryDescription() {
        return "No primary description set yet.";
    }

    default String getSecondaryDescription() {
        return "No secondary description set yet.";
    }

    default String getUltimateDescription() {
        return "No ultimate description set yet.";
    }
}