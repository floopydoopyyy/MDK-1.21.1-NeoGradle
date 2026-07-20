package com.oliver.witchmod.items;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;

/**
 * Deflects incoming curses back to the caster (CLAUDE.md section 3). The actual redirect happens in
 * {@code data.EffectManager} via the hook registered in {@code WitchMod}'s constructor — this class just
 * supplies the "does this player have one" check and the consume-on-deflect side effects.
 */
public final class ItemWard extends Item {
    public ItemWard(Properties properties) {
        super(properties);
    }

    public static boolean hasActiveWard(ServerPlayer player) {
        return findWard(player) != null;
    }

    public static void onDeflect(ServerPlayer target, ServerPlayer attacker) {
        ItemStack ward = findWard(target);
        if (ward == null) {
            return;
        }
        target.displayClientMessage(Component.literal("Your Ward deflects the curse back at " + attacker.getName().getString() + "!"), true);

        ServerLevel level = target.serverLevel();
        Vec3 toward = attacker.position().subtract(target.position());
        if (toward.lengthSqr() > 1.0E-4) {
            Vec3 dir = toward.normalize();
            for (int i = 1; i <= 5; i++) {
                Vec3 point = target.position().add(dir.scale(i * 0.6)).add(0, 1.0, 0);
                level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }

        if (Config.WARD_DURABILITY_DECAYS.get()) {
            ward.hurtAndBreak(1, level, target, item -> target.displayClientMessage(Component.literal("Your Ward shatters."), true));
        }
    }

    @Nullable
    private static ItemStack findWard(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ItemWard) {
                return stack;
            }
        }
        return null;
    }
}
