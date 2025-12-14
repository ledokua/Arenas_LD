package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.ledok.YggdrasilLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

public record BossDataComponent(int bossLevel) {
    public static final Codec<BossDataComponent> CODEC = Codec.INT.xmap(BossDataComponent::new, BossDataComponent::bossLevel);

    // --- MIGRATED DATA COMPONENT ---
    public static final DataComponentType<BossDataComponent> BOSS_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(YggdrasilLdMod.MOD_ID, "boss_data"),
            DataComponentType.<BossDataComponent>builder()
                    .persistent(CODEC) // Renamed from codec()
                    .networkSynchronized(ByteBufCodecs.fromCodec(CODEC)) // Replaced packetCodec(PacketCodecs.codec(...))
                    .build()
    );

    public static void initialize() {
        // This method ensures the static fields are loaded when your mod initializes.
    }
}