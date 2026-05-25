package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoomControllerSnapshotPayload(RoomControllerData data) implements CustomPacketPayload {
    public static final Type<RoomControllerSnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_controller_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomControllerSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            RoomControllerData.STREAM_CODEC,
            RoomControllerSnapshotPayload::data,
            RoomControllerSnapshotPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
