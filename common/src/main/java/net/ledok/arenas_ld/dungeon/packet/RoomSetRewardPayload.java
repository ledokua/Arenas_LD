package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomSetRewardPayload(BlockPos blockPos, RoomRewardConfig reward) implements CustomPacketPayload {
    public static final Type<RoomSetRewardPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_set_reward"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomSetRewardPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomSetRewardPayload::blockPos,
            RoomRewardConfig.STREAM_CODEC, RoomSetRewardPayload::reward,
            RoomSetRewardPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
