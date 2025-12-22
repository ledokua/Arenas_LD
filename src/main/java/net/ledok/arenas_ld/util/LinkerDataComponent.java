package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

public record LinkerDataComponent(String groupId) {
    public static final Codec<LinkerDataComponent> CODEC = Codec.STRING.xmap(LinkerDataComponent::new, LinkerDataComponent::groupId);

    public static final DataComponentType<LinkerDataComponent> LINKER_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "linker_data"),
            DataComponentType.<LinkerDataComponent>builder()
                    .persistent(CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(CODEC))
                    .build()
    );

    public static void initialize() {
    }
}
