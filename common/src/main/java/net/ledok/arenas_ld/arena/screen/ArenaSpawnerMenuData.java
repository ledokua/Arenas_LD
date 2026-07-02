package net.ledok.arenas_ld.arena.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Screen-opening payload for the arena spawner editor; the screen reads config from the synced BE. */
public record ArenaSpawnerMenuData(BlockPos blockPos) {
    public static final StreamCodec<FriendlyByteBuf, ArenaSpawnerMenuData> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeBlockPos(value.blockPos()),
        buf -> new ArenaSpawnerMenuData(buf.readBlockPos()));
}
