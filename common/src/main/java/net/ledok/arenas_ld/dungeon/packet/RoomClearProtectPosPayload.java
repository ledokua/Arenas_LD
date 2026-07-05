package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomClearProtectPosPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<RoomClearProtectPosPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_clear_protect_pos"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomClearProtectPosPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomClearProtectPosPayload::blockPos,
            RoomClearProtectPosPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
