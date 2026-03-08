package net.ledok.arenas_ld.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MobArenaControllerData(BlockPos pos) {
    public static final StreamCodec<FriendlyByteBuf, MobArenaControllerData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> buf.writeBlockPos(data.pos),
            buf -> new MobArenaControllerData(buf.readBlockPos())
    );
}
