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

/** Your feet just aren't leaving the ground the way they used to. */
public final class CurseHeavy extends Effect {
    private static final ResourceLocation MODIFIER_ID = EffectUtil.modifierId("curse_heavy");

    public CurseHeavy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.IRON_BLOCK);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.JUMP_STRENGTH, MODIFIER_ID, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.JUMP_STRENGTH, MODIFIER_ID);
    }
}
