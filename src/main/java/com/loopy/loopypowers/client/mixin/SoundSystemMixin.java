package com.loopy.loopypowers.client.mixin;

import com.loopy.loopypowers.client.fx.StunAudioClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {

    @Redirect(
            method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"
            )
    )
    private float loopypowers$stunAudioDampen(SoundInstance instance) {
        return instance.getVolume() * StunAudioClient.volumeMultiplier();
    }
}