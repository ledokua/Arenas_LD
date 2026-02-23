package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record LootBundleDataComponent(String lootTableId) {
    public static final Codec<LootBundleDataComponent> CODEC = Codec.STRING.xmap(LootBundleDataComponent::new, LootBundleDataComponent::lootTableId);
    public static final StreamCodec<io.netty.buffer.ByteBuf, LootBundleDataComponent> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
