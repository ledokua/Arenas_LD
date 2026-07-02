package net.ledok.arenas_ld.arena.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Screen-opening payload for the arena controller. Carries only the controller position; the owo
 * screen reads live lobby/instance state from the client-synced {@code ArenaControllerBlockEntity}.
 */
public record ArenaControllerMenuData(BlockPos blockPos) {
    public static final StreamCodec<FriendlyByteBuf, ArenaControllerMenuData> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeBlockPos(value.blockPos()),
        buf -> new ArenaControllerMenuData(buf.readBlockPos()));
}
