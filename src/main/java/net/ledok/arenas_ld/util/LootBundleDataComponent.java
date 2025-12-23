package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

public record LootBundleDataComponent(String lootTableId) {
    public static final Codec<LootBundleDataComponent> CODEC = Codec.STRING.xmap(LootBundleDataComponent::new, LootBundleDataComponent::lootTableId);

    public static final DataComponentType<LootBundleDataComponent> LOOT_BUNDLE_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "loot_bundle_data"),
            DataComponentType.<LootBundleDataComponent>builder()
                    .persistent(CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(CODEC))
                    .build()
    );

    public static void initialize() {
    }
}
