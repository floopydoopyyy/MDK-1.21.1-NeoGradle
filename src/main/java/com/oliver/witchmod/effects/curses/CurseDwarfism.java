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

/** You're just... smaller now. */
public final class CurseDwarfism extends Effect {
    private static final ResourceLocation MODIFIER_ID = EffectUtil.modifierId("curse_dwarfism");

    public CurseDwarfism() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.TURTLE_EGG);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.SCALE, MODIFIER_ID, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.SCALE, MODIFIER_ID);
    }
}
