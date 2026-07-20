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

/**
 * You hit like a truck and shrug off knockback like one too.
 *
 * <p>Sacrificial item deliberately differs from {@link CurseHeavy} (Iron Block): CLAUDE.md flags Heavy
 * and Heavyweight as an unresolved collision in section 6.1 since both curses shared the same item and
 * occupy the same Sacrificial Item pool. Resolved here by giving Heavyweight its own item.
 */
public final class CurseHeavyweight extends Effect {
    private static final ResourceLocation KNOCKBACK_MODIFIER_ID = EffectUtil.modifierId("curse_heavyweight_knockback");
    private static final ResourceLocation RESISTANCE_MODIFIER_ID = EffectUtil.modifierId("curse_heavyweight_resistance");

    public CurseHeavyweight() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.NETHERITE_BLOCK);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        EffectUtil.addModifier(target, Attributes.ATTACK_KNOCKBACK, KNOCKBACK_MODIFIER_ID, 1.5, AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(target, Attributes.KNOCKBACK_RESISTANCE, RESISTANCE_MODIFIER_ID, 0.5, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.ATTACK_KNOCKBACK, KNOCKBACK_MODIFIER_ID);
        EffectUtil.removeModifier(target, Attributes.KNOCKBACK_RESISTANCE, RESISTANCE_MODIFIER_ID);
    }
}
