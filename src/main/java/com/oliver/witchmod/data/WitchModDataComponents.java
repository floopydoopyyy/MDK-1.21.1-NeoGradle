package com.oliver.witchmod.data;

import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** Custom item data components for Player Essence/Voodoo Doll binding and Jar-captured effects. */
public final class WitchModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WitchMod.MODID);

    /** Which player a Player Essence or Voodoo Doll is bound to; absent when unbound. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlayerEssenceData>> BOUND_PLAYER =
            DATA_COMPONENTS.registerComponentType("bound_player", builder -> builder
                    .persistent(PlayerEssenceData.CODEC)
                    .networkSynchronized(PlayerEssenceData.STREAM_CODEC));

    /** Curses a Jar/Cursed Jar has captured off a player, awaiting release. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<CapturedEffect>>> CAPTURED_EFFECTS =
            DATA_COMPONENTS.registerComponentType("captured_effects", builder -> builder
                    .persistent(CapturedEffect.CODEC.listOf())
                    .networkSynchronized(CapturedEffect.STREAM_CODEC.apply(ByteBufCodecs.list(16))));

    private WitchModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
