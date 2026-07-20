package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Every time you talk, the whole server hears a laugh track. You're hilarious now.
 *
 * <p>PROTOTYPE: the full spec plays one of three custom laugh-track variants server-wide (Section 12) when
 * the blessed player sends a chat message. Implemented in {@link com.oliver.witchmod.effects.BlessingEventHandler}
 * via the chat event, using a vanilla sound stand-in until the OGGs exist. This class only declares
 * cost/sacrificial item.
 */
public final class BlessingLaughTrack extends Effect {
    public static final int COOLDOWN_TICKS = 200;

    public BlessingLaughTrack() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.COCOA_BEANS);
    }
}
