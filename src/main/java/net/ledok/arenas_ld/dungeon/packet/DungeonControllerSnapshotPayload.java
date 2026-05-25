package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonControllerSnapshotPayload(DungeonControllerData data) implements CustomPacketPayload {
    public static final Type<DungeonControllerSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_controller_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonControllerSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    DungeonControllerData.STREAM_CODEC,
                    DungeonControllerSnapshotPayload::data,
                    DungeonControllerSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
