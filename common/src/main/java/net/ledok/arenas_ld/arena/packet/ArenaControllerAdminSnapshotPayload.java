package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.screen.ArenaControllerAdminData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → open arena admin screens ({@code AdminScreens.viewers}): fresh {@link ArenaControllerAdminData}. */
public record ArenaControllerAdminSnapshotPayload(ArenaControllerAdminData data) implements CustomPacketPayload {
    public static final Type<ArenaControllerAdminSnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_controller_admin_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaControllerAdminSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(ArenaControllerAdminData.STREAM_CODEC, ArenaControllerAdminSnapshotPayload::data,
            ArenaControllerAdminSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
