package com.loopy.loopypowers.mixin;

import com.loopy.loopypowers.manager.PassiveManager;
import com.loopy.loopypowers.manager.PowerManager;
import com.loopy.loopypowers.power.ExplosionPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class EntityExplodeResistMixin {

    @Unique
    private static final float EXPLOSION_MULT = 0.20f; // amount of damage resisted

    @Unique
    @ModifyVariable(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            index = 2,          // 0=this, 1=DamageSource, 2=float amount
            argsOnly = true    // IMPORTANT for index=2 + signature below
    )
    private float loopypowers$reduceExplosionDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;

        // only server-side players
        if (!(self instanceof ServerPlayer player)) return amount;

        // only explosion damage
        if (!source.is(DamageTypeTags.IS_EXPLOSION)) return amount;

        // only if player currently has ExplosionPower
        if (!(PowerManager.getPower(player) instanceof ExplosionPower)) return amount;

        // and must have passive on (fixed bug from original code where power was checked twice)
        if (!PassiveManager.isEnabled(player)) return amount;

        return amount * EXPLOSION_MULT;
    }
}