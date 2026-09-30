package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The Dungeon Tool's held state: active mode plus the selected source block (with its dimension).
 * When the source is a room controller, {@code spawnerPos} may additionally hold one of the
 * room's linked spawners, picked from the tool screen's list: MOB_SPAWN_POSITION clicks then act
 * on that spawner while the room selection is kept. Always in the source's dimension; cleared
 * whenever the source changes.
 */
public record DungeonToolDataComponent(
        int mode,
        Optional<BlockPos> sourcePos,
        Optional<ResourceKey<Level>> sourceDimension,
        Optional<BlockPos> spawnerPos
) {
    public static final DungeonToolDataComponent DEFAULT =
        new DungeonToolDataComponent(0, Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<DungeonToolDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(DungeonToolDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("source_pos").forGetter(DungeonToolDataComponent::sourcePos),
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("source_dimension").forGetter(DungeonToolDataComponent::sourceDimension),
                    BlockPos.CODEC.optionalFieldOf("spawner_pos").forGetter(DungeonToolDataComponent::spawnerPos)
            ).apply(instance, DungeonToolDataComponent::new)
    );

    public static final StreamCodec<io.netty.buffer.ByteBuf, DungeonToolDataComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DungeonToolDataComponent::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            DungeonToolDataComponent::sourcePos,
            ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.DIMENSION)),
            DungeonToolDataComponent::sourceDimension,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            DungeonToolDataComponent::spawnerPos,
            DungeonToolDataComponent::new
    );

    public DungeonToolDataComponent withMode(int mode) {
        return new DungeonToolDataComponent(mode, sourcePos, sourceDimension, spawnerPos);
    }

    /** Selecting a new source drops any picked spawner — it belonged to the old room. */
    public DungeonToolDataComponent withSource(BlockPos pos, ResourceKey<Level> dimension) {
        return new DungeonToolDataComponent(mode, Optional.of(pos), Optional.of(dimension), Optional.empty());
    }

    public DungeonToolDataComponent withoutSource() {
        return new DungeonToolDataComponent(mode, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public DungeonToolDataComponent withSpawner(BlockPos pos) {
        return new DungeonToolDataComponent(mode, sourcePos, sourceDimension, Optional.of(pos));
    }
}
