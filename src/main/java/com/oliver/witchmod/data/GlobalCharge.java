package com.oliver.witchmod.data;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import com.oliver.witchmod.Config;

/**
 * The shared, server-wide "charge" that governs Global events (master-spec Section 8, per Oliver's
 * clarification of the "global bank"): globals do NOT cost a currency. Instead all globals share ONE
 * server-wide cooldown — right after any global fires the success chance is ~0, and it slowly climbs to a
 * configurable base ceiling over a few hours of world runtime. The chance is identical for every player
 * (it's this single stored value), and a global firing {@link #reset}s it so the wait begins again.
 *
 * <p>Persisted via the overworld's {@link net.minecraft.world.level.storage.DimensionDataStorage} so it's
 * genuinely one value for the whole server and survives restarts.
 */
public final class GlobalCharge extends SavedData {
    private static final String NAME = "witchmod_global_charge";

    /** Overworld game-time tick the current recharge cycle started from (0 = "always been charging"). */
    private long cycleStartTick;

    public GlobalCharge() {}

    private GlobalCharge(long cycleStartTick) {
        this.cycleStartTick = cycleStartTick;
    }

    public static GlobalCharge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>((Supplier<GlobalCharge>) GlobalCharge::new,
                        (BiFunction<CompoundTag, HolderLookup.Provider, GlobalCharge>) GlobalCharge::load),
                NAME);
    }

    private static GlobalCharge load(CompoundTag tag, HolderLookup.Provider registries) {
        return new GlobalCharge(tag.getLong("cycleStartTick"));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("cycleStartTick", cycleStartTick);
        return tag;
    }

    /** 0..1 ramp across the recharge window — the same for every player, keyed to overworld game time. */
    public float chargeFactor(MinecraftServer server) {
        long rechargeTicks = (long) Config.GLOBAL_RECHARGE_HOURS.get() * 60L * 60L * 20L;
        if (rechargeTicks <= 0L) {
            return 1.0F;
        }
        long elapsed = server.overworld().getGameTime() - cycleStartTick;
        return (float) Mth.clamp((double) elapsed / rechargeTicks, 0.0, 1.0);
    }

    /** The current server-wide success chance for triggering a global: {@code baseChance * chargeFactor}. */
    public float currentSuccessChance(MinecraftServer server) {
        return (Config.GLOBAL_BASE_CHANCE_PERCENT.get() / 100.0F) * chargeFactor(server);
    }

    /** Call when a global actually fires: restarts the shared cooldown so the chance climbs from ~0 again. */
    public void reset(MinecraftServer server) {
        this.cycleStartTick = server.overworld().getGameTime();
        setDirty();
    }
}
