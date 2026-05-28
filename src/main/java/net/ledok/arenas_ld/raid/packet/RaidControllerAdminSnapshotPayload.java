package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.raid.screen.RaidControllerAdminData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidControllerAdminSnapshotPayload(RaidControllerAdminData data) implements CustomPacketPayload {
    public static final Type<RaidControllerAdminSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_controller_admin_snapshot")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidControllerAdminSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
        RaidControllerAdminData.STREAM_CODEC, RaidControllerAdminSnapshotPayload::data,
        RaidControllerAdminSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
