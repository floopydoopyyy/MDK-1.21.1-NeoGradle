package com.oliver.witchmod.effects.curses;

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

/** You never quite stick the landing anymore. */
public final class CurseClumsy extends Effect {
    private static final ResourceLocation MODIFIER_ID = EffectUtil.modifierId("curse_clumsy");

    public CurseClumsy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.EGG);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.SAFE_FALL_DISTANCE, MODIFIER_ID, -3.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.SAFE_FALL_DISTANCE, MODIFIER_ID);
    }
}
