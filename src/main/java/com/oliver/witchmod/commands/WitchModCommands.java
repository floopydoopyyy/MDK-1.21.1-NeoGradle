package com.oliver.witchmod.commands;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.oliver.witchmod.WitchMod;

@EventBusSubscriber(modid = WitchMod.MODID)
public final class WitchModCommands {
    private WitchModCommands() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        BewitchCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
