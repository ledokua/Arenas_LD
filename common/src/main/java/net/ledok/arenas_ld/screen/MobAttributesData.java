package net.ledok.arenas_ld.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MobAttributesData(BlockPos pos) {
    public static final StreamCodec<FriendlyByteBuf, MobAttributesData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> buf.writeBlockPos(data.pos),
            buf -> new MobAttributesData(buf.readBlockPos())
    );
}
