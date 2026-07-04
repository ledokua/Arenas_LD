package net.ledok.arenas_ld.dungeon.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One status effect granted by a room's clear reward. {@code amplifier} is the raw MC
 * amplifier (0 = level I); screens display it as level = amplifier + 1.
 */
public record RoomEffectData(String effectId, int durationSeconds, int amplifier) {
    public static final Codec<RoomEffectData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("effectId").forGetter(RoomEffectData::effectId),
        Codec.INT.optionalFieldOf("durationSeconds", 30).forGetter(RoomEffectData::durationSeconds),
        Codec.INT.optionalFieldOf("amplifier", 0).forGetter(RoomEffectData::amplifier)
    ).apply(i, RoomEffectData::new));
}
