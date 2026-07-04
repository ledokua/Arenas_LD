package net.ledok.arenas_ld.dungeon.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Reward granted to each loot-eligible party player when a dungeon room is cleared.
 * All fields default to "nothing" — an untouched room grants no reward.
 *
 * <p>{@code commands} run as the server on room clear; {@code @dungeonplayer} (or {@code @s})
 * is replaced with each eligible player's name and the command runs once per player.
 */
public record RoomRewardConfig(String lootTableId, List<RoomEffectData> effects, long currency, int skillXp,
                               List<String> commands) {

    public static final RoomRewardConfig EMPTY = new RoomRewardConfig("", List.of(), 0L, 0, List.of());

    public static final Codec<RoomRewardConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("lootTableId", "").forGetter(RoomRewardConfig::lootTableId),
        RoomEffectData.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(RoomRewardConfig::effects),
        Codec.LONG.optionalFieldOf("currency", 0L).forGetter(RoomRewardConfig::currency),
        Codec.INT.optionalFieldOf("skillXp", 0).forGetter(RoomRewardConfig::skillXp),
        Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(RoomRewardConfig::commands)
    ).apply(i, RoomRewardConfig::new));

    // NBT-via-CODEC, same wire approach as SetTierConfigPayload; exposed as a StreamCodec so it
    // composes into both RoomSetRewardPayload and RoomControllerData.
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomRewardConfig> STREAM_CODEC = StreamCodec.of(
        (buf, cfg) -> buf.writeNbt((CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, cfg).getOrThrow()),
        buf -> {
            CompoundTag tag = buf.readNbt();
            return tag == null ? EMPTY : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(EMPTY);
        }
    );

    public boolean isEmpty() {
        return lootTableId.isEmpty() && effects.isEmpty() && currency <= 0L && skillXp <= 0 && commands.isEmpty();
    }
}
