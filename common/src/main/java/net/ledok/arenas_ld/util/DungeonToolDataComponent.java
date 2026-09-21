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

/** The Dungeon Tool's held state: active mode plus the selected source block (with its dimension). */
public record DungeonToolDataComponent(
        int mode,
        Optional<BlockPos> sourcePos,
        Optional<ResourceKey<Level>> sourceDimension
) {
    public static final DungeonToolDataComponent DEFAULT = new DungeonToolDataComponent(0, Optional.empty(), Optional.empty());

    public static final Codec<DungeonToolDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(DungeonToolDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("source_pos").forGetter(DungeonToolDataComponent::sourcePos),
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("source_dimension").forGetter(DungeonToolDataComponent::sourceDimension)
            ).apply(instance, DungeonToolDataComponent::new)
    );

    public static final StreamCodec<io.netty.buffer.ByteBuf, DungeonToolDataComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DungeonToolDataComponent::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            DungeonToolDataComponent::sourcePos,
            ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.DIMENSION)),
            DungeonToolDataComponent::sourceDimension,
            DungeonToolDataComponent::new
    );

    public DungeonToolDataComponent withMode(int mode) {
        return new DungeonToolDataComponent(mode, sourcePos, sourceDimension);
    }

    public DungeonToolDataComponent withSource(BlockPos pos, ResourceKey<Level> dimension) {
        return new DungeonToolDataComponent(mode, Optional.of(pos), Optional.of(dimension));
    }

    public DungeonToolDataComponent withoutSource() {
        return new DungeonToolDataComponent(mode, Optional.empty(), Optional.empty());
    }
}
