package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomRemoveSpawnerPayload(BlockPos blockPos, BlockPos spawnerPos) implements CustomPacketPayload {
    public static final Type<RoomRemoveSpawnerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_remove_spawner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomRemoveSpawnerPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomRemoveSpawnerPayload::blockPos,
            BlockPos.STREAM_CODEC, RoomRemoveSpawnerPayload::spawnerPos,
            RoomRemoveSpawnerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
