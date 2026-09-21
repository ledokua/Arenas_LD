package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.util.LinkerDataComponent;
import net.ledok.arenas_ld.util.DungeonToolDataComponent;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.function.UnaryOperator;

public class DataComponentRegistry {

    public static final DataComponentType<LinkerDataComponent> LINKER_DATA = register("linker_data", builder -> builder.persistent(LinkerDataComponent.CODEC).networkSynchronized(LinkerDataComponent.STREAM_CODEC));
    public static final DataComponentType<DungeonToolDataComponent> DUNGEON_TOOL_DATA = register("dungeon_tool_data", builder -> builder.persistent(DungeonToolDataComponent.CODEC).networkSynchronized(DungeonToolDataComponent.STREAM_CODEC));
    public static final DataComponentType<LootBundleDataComponent> LOOT_BUNDLE_DATA = register("loot_bundle_data", builder -> builder.persistent(LootBundleDataComponent.CODEC).networkSynchronized(LootBundleDataComponent.STREAM_CODEC));

    private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> operator) {
        return RegistryBridge.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, id), operator.apply(DataComponentType.builder()).build());
    }

    public static void initialize() {
        // This method is called to ensure the static initializers are run
    }
}
