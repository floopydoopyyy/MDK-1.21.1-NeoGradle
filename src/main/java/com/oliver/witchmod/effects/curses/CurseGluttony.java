package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * You're always hungry, and it shows — literally (master-spec Gluttony, a CUSTOM UI curse, Phase D). Adds a
 * second "stomach" hunger row (the auto-synced {@link WitchModAttachments#GLUTTONY_HUNGER} attribute,
 * rendered by {@code client/GluttonyHudLayer}), scales the model up, and cuts sprinting off early.
 *
 * <p>The extra bar drains faster than normal hunger and is topped up by eating (handled in
 * {@code CurseEventHandler}); when it empties you get periodic Hunger. Sprint is cut off while your normal
 * food is at/below {@value #SPRINT_CUTOFF} (double the vanilla threshold of 6).
 */
public final class CurseGluttony extends Effect {
    public static final int EXTRA_MAX = 20;
    private static final int SPRINT_CUTOFF = 12;
    private static final double MODEL_SCALE_BONUS = 0.35; // scale attribute 1.0 + 0.35 = 1.35x
    private static final int DRAIN_INTERVAL_TICKS = 130;  // ~1.5x faster than a comparable bar
    private static final int EMPTY_EFFECT_INTERVAL = 40;

    private static final ResourceLocation SCALE_MODIFIER_ID = EffectUtil.modifierId("curse_gluttony_scale");

    public CurseGluttony() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.CAKE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.GLUTTONY_HUNGER, EXTRA_MAX);
        EffectUtil.addModifier(target, Attributes.SCALE, SCALE_MODIFIER_ID, MODEL_SCALE_BONUS, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.GLUTTONY_HUNGER, -1); // hide the extra row
        EffectUtil.removeModifier(target, Attributes.SCALE, SCALE_MODIFIER_ID);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int extra = target.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra < 0) {
            extra = EXTRA_MAX; // self-heal (relog — onApply doesn't re-run)
        }
        if (EffectUtil.every(ticksRemaining, DRAIN_INTERVAL_TICKS) && extra > 0) {
            extra--;
        }
        target.setData(WitchModAttachments.GLUTTONY_HUNGER, extra);

        // Empty extra stomach = constant gnawing hunger.
        if (extra == 0 && EffectUtil.every(ticksRemaining, EMPTY_EFFECT_INTERVAL)) {
            EffectUtil.addTimedEffect(target, MobEffects.HUNGER, 60, 0);
        }

        // Sprint cutoff at double the vanilla threshold.
        if (target.isSprinting() && target.getFoodData().getFoodLevel() <= SPRINT_CUTOFF) {
            target.setSprinting(false);
        }
    }

    /** Adds to the extra stomach (clamped), only while active. Used by the eating hook to refill it. */
    public static void feed(ServerPlayer player, int nutrition) {
        int extra = player.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra < 0) {
            return;
        }
        player.setData(WitchModAttachments.GLUTTONY_HUNGER, Math.min(EXTRA_MAX, extra + nutrition));
    }
}
