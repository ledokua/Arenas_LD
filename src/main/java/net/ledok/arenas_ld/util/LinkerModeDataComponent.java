package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.function.Supplier;

public record LinkerModeDataComponent(int mode, Optional<BlockPos> mainSpawnerPos) {

    public static final LinkerModeDataComponent DEFAULT = new LinkerModeDataComponent(0, Optional.empty());

    public static final Codec<LinkerModeDataComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("mode").forGetter(LinkerModeDataComponent::mode),
                    BlockPos.CODEC.optionalFieldOf("main_spawner_pos").forGetter(LinkerModeDataComponent::mainSpawnerPos)
            ).apply(instance, LinkerModeDataComponent::new)
    );

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, ArenasLdMod.MOD_ID);

    public static final Supplier<DataComponentType<LinkerModeDataComponent>> LINKER_MODE_DATA = DATA_COMPONENT_TYPES.register("linker_mode_data", () ->
            DataComponentType.<LinkerModeDataComponent>builder()
                    .persistent(CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(CODEC))
                    .build());

    public static void initialize(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
