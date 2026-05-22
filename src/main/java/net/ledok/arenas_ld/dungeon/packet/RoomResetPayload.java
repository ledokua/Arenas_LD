package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomResetPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<RoomResetPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_reset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomResetPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomResetPayload::blockPos,
            RoomResetPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
