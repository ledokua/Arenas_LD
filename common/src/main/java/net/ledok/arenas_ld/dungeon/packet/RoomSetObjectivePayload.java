package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomSetObjectivePayload(BlockPos blockPos, RoomObjectiveConfig objective) implements CustomPacketPayload {
    public static final Type<RoomSetObjectivePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_set_objective"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomSetObjectivePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomSetObjectivePayload::blockPos,
            RoomObjectiveConfig.STREAM_CODEC, RoomSetObjectivePayload::objective,
            RoomSetObjectivePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
