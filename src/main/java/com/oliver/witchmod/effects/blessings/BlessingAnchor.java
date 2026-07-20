package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/** You're planted. Nothing's knocking you off your feet right now. */
public final class BlessingAnchor extends Effect {
    private static final ResourceLocation MODIFIER_ID = EffectUtil.modifierId("blessing_anchor");

    public BlessingAnchor() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CHAIN);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.KNOCKBACK_RESISTANCE, MODIFIER_ID, 0.6, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.KNOCKBACK_RESISTANCE, MODIFIER_ID);
    }
}
