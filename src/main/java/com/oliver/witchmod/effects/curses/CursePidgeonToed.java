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

/** You waddle now. It's just how you walk. */
public final class CursePidgeonToed extends Effect {
    private static final ResourceLocation MODIFIER_ID = EffectUtil.modifierId("curse_pidgeon_toed");

    public CursePidgeonToed() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.FEATHER);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.MOVEMENT_SPEED, MODIFIER_ID, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, MODIFIER_ID);
    }
}
