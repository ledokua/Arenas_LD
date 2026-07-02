package net.ledok.arenas_ld.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record EquipmentScreenData(BlockPos pos) {
    public static final StreamCodec<FriendlyByteBuf, EquipmentScreenData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> buf.writeBlockPos(data.pos),
            buf -> new EquipmentScreenData(buf.readBlockPos())
    );
}
