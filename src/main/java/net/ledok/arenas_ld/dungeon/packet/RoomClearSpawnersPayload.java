package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomClearSpawnersPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<RoomClearSpawnersPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_clear_spawners"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomClearSpawnersPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomClearSpawnersPayload::blockPos,
            RoomClearSpawnersPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
