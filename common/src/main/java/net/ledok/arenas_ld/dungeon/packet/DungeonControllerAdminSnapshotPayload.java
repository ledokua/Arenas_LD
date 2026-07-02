package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonControllerAdminSnapshotPayload(DungeonControllerAdminData data) implements CustomPacketPayload {
    public static final Type<DungeonControllerAdminSnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_controller_admin_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonControllerAdminSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            DungeonControllerAdminData.STREAM_CODEC,
            DungeonControllerAdminSnapshotPayload::data,
            DungeonControllerAdminSnapshotPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
