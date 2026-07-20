package com.oliver.witchmod.data;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Which player a Player Essence, Voodoo Doll, etc. is bound to (CLAUDE.md section 3). */
public record PlayerEssenceData(UUID playerId, String playerName) {
    public static final Codec<PlayerEssenceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player_id").forGetter(PlayerEssenceData::playerId),
            Codec.STRING.fieldOf("player_name").forGetter(PlayerEssenceData::playerName))
            .apply(instance, PlayerEssenceData::new));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PlayerEssenceData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlayerEssenceData::playerId,
            ByteBufCodecs.STRING_UTF8, PlayerEssenceData::playerName,
            PlayerEssenceData::new);
}
