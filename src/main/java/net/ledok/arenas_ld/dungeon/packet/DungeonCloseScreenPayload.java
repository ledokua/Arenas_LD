package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: tells the client to close the dungeon controller screen for the given controller (sent to
 * every party member when their run launches).
 */
public record DungeonCloseScreenPayload(BlockPos controllerPos) implements CustomPacketPayload {
    public static final Type<DungeonCloseScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_close_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonCloseScreenPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, DungeonCloseScreenPayload::controllerPos,
            DungeonCloseScreenPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
