package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.screen.RaidControllerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidControllerSnapshotPayload(RaidControllerData data) implements CustomPacketPayload {
    public static final Type<RaidControllerSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_controller_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidControllerSnapshotPayload> STREAM_CODEC =
        RaidControllerData.STREAM_CODEC.map(RaidControllerSnapshotPayload::new, RaidControllerSnapshotPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
