package com.loopy.loopypowers.manager;

import com.loopy.loopypowers.Loopypowers;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Loopypowers.MOD_ID);

    /**
     * Stores power, level, cd multiplier, and passive toggle on the player entity.
     * NeoForge serializes/deserializes this automatically via PlayerPowerData's
     * INBTSerializable implementation, scoping it to the current world save.
     *
     * copyOnDeath() is intentionally included so players keep their power on death
     * (matching the existing behaviour). Remove it if you want powers to reset on death.
     */
    public static final Supplier<AttachmentType<PlayerPowerData>> POWER_DATA =
            ATTACHMENT_TYPES.register("power_data", () ->
                    AttachmentType.serializable(PlayerPowerData::new)
                            .copyOnDeath()
                            .build());
}