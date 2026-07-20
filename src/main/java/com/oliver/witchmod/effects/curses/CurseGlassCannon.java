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

/** You hit hard. You also break like one. */
public final class CurseGlassCannon extends Effect {
    private static final ResourceLocation DAMAGE_MODIFIER_ID = EffectUtil.modifierId("curse_glass_cannon_damage");
    private static final ResourceLocation ARMOR_MODIFIER_ID = EffectUtil.modifierId("curse_glass_cannon_armor");

    public CurseGlassCannon() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.GLASS);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        EffectUtil.addModifier(target, Attributes.ARMOR, ARMOR_MODIFIER_ID, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER_ID);
        EffectUtil.removeModifier(target, Attributes.ARMOR, ARMOR_MODIFIER_ID);
    }
}
