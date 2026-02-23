package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record LinkerDataComponent(String groupId) {
    public static final Codec<LinkerDataComponent> CODEC = Codec.STRING.xmap(LinkerDataComponent::new, LinkerDataComponent::groupId);
    public static final StreamCodec<io.netty.buffer.ByteBuf, LinkerDataComponent> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
