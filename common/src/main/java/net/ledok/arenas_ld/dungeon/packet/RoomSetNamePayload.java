package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomSetNamePayload(BlockPos blockPos, String name) implements CustomPacketPayload {
    public static final Type<RoomSetNamePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_set_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomSetNamePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomSetNamePayload::blockPos,
            ByteBufCodecs.STRING_UTF8, RoomSetNamePayload::name,
            RoomSetNamePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
