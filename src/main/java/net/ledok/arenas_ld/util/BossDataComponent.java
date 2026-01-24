package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public record BossDataComponent(int bossLevel) {
    public static final Codec<BossDataComponent> CODEC = Codec.INT.xmap(BossDataComponent::new, BossDataComponent::bossLevel);

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, ArenasLdMod.MOD_ID);

    public static final Supplier<DataComponentType<BossDataComponent>> BOSS_DATA = DATA_COMPONENT_TYPES.register("boss_data", () ->
            DataComponentType.<BossDataComponent>builder()
                    .persistent(CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(CODEC))
                    .build());

    public static void initialize(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
