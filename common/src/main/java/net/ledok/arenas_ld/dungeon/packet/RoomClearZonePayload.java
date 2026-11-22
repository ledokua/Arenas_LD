package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Screen → server: clear the room's goal zone (4.44.0). */
public record RoomClearZonePayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<RoomClearZonePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "room_clear_zone"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomClearZonePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomClearZonePayload::blockPos,
            RoomClearZonePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
