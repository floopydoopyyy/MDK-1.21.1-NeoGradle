package com.loopy.loopypowers.ritual;

import net.minecraft.server.level.ServerLevel;

public interface RitualInterface {
    boolean tick(ServerLevel world); // return true when finished
}