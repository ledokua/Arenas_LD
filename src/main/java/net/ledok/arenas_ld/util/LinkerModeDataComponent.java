package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponentType;

import java.util.Optional;

public record LinkerModeDataComponent(int mode, Optional<BlockPos> mainSpawnerPos) {

    public static final LinkerModeDataComponent DEFAULT = new LinkerModeDataComponent(0, Optional.empty());

    public static final Codec<LinkerModeDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(LinkerModeDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("main_spawner_pos").forGetter(LinkerModeDataComponent::mainSpawnerPos)
            ).apply(instance, LinkerModeDataComponent::new)
    );

    public static final DataComponentType<LinkerModeDataComponent> LINKER_MODE_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "linker_mode_data"),
            DataComponentType.<LinkerModeDataComponent>builder()
                    .persistent(CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(CODEC))
                    .build()
    );

    public static void initialize() {
    }
}
