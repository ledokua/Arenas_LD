package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record LinkerModeDataComponent(int mode, Optional<BlockPos> mainSpawnerPos, Optional<ResourceKey<Level>> mainSpawnerDimension) {
    public static final LinkerModeDataComponent DEFAULT = new LinkerModeDataComponent(0, Optional.empty(), Optional.empty());

    public static final Codec<LinkerModeDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(LinkerModeDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("main_spawner_pos").forGetter(LinkerModeDataComponent::mainSpawnerPos),
                    ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).optionalFieldOf("main_spawner_dimension").forGetter(LinkerModeDataComponent::mainSpawnerDimension)
            ).apply(instance, LinkerModeDataComponent::new)
    );

    public static final StreamCodec<io.netty.buffer.ByteBuf, LinkerModeDataComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            LinkerModeDataComponent::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            LinkerModeDataComponent::mainSpawnerPos,
            ByteBufCodecs.optional(ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION)),
            LinkerModeDataComponent::mainSpawnerDimension,
            LinkerModeDataComponent::new
    );
}
