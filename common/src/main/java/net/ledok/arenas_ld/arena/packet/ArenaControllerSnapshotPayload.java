package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.screen.ArenaControllerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → open arena lobby screens: the viewer's fresh {@link ArenaControllerData}. */
public record ArenaControllerSnapshotPayload(ArenaControllerData data) implements CustomPacketPayload {
    public static final Type<ArenaControllerSnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_controller_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaControllerSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(ArenaControllerData.STREAM_CODEC, ArenaControllerSnapshotPayload::data,
            ArenaControllerSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
