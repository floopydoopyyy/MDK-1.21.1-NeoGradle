package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * You just... go off sometimes. Leaves the world alone (no block damage); doesn't leave you alone.
 *
 * <p>Sacrificial item is Gunpowder per master-spec Section 5 (Explosive = Gunpowder, Super Explosive =
 * TNT). The prototype originally used TNT here; corrected during Phase A when Super Explosive (which the
 * spec assigns TNT) was built, so the two no longer collide on the same sacrificial item.
 */
public final class CurseExplosive extends Effect {
    private static final int INTERVAL_TICKS = 400;

    public CurseExplosive() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 65, () -> Items.GUNPOWDER);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            ServerLevel level = target.serverLevel();
            level.explode(target, target.getX(), target.getY(), target.getZ(), 1.5F, Level.ExplosionInteraction.NONE);
        }
    }
}
