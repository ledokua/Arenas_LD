package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

public record LinkerModeDataComponent(int mode, Optional<BlockPos> mainSpawnerPos) {

    public static final LinkerModeDataComponent DEFAULT = new LinkerModeDataComponent(0, Optional.empty());

    public static final Codec<LinkerModeDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(LinkerModeDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("main_spawner_pos").forGetter(LinkerModeDataComponent::mainSpawnerPos)
            ).apply(instance, LinkerModeDataComponent::new)
    );

    public static final StreamCodec<io.netty.buffer.ByteBuf, LinkerModeDataComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            LinkerModeDataComponent::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            LinkerModeDataComponent::mainSpawnerPos,
            LinkerModeDataComponent::new
    );
}
