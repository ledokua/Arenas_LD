package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record SpawnerSelectionDataComponent(int mode, Optional<BlockPos> selectedSpawnerPos, Optional<ResourceKey<Level>> selectedSpawnerDimension) {
    public static final SpawnerSelectionDataComponent DEFAULT = new SpawnerSelectionDataComponent(0, Optional.empty(), Optional.empty());

    public static final Codec<SpawnerSelectionDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(SpawnerSelectionDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("selected_spawner_pos").forGetter(SpawnerSelectionDataComponent::selectedSpawnerPos),
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("selected_spawner_dimension").forGetter(SpawnerSelectionDataComponent::selectedSpawnerDimension)
            ).apply(instance, SpawnerSelectionDataComponent::new)
    );

    public static final StreamCodec<io.netty.buffer.ByteBuf, SpawnerSelectionDataComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SpawnerSelectionDataComponent::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            SpawnerSelectionDataComponent::selectedSpawnerPos,
            ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.DIMENSION)),
            SpawnerSelectionDataComponent::selectedSpawnerDimension,
            SpawnerSelectionDataComponent::new
    );
}
