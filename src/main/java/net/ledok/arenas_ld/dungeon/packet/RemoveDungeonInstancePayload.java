package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoveDungeonInstancePayload(BlockPos controllerPos, BlockPos instancePos) implements CustomPacketPayload {
    public static final Type<RemoveDungeonInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_remove_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveDungeonInstancePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RemoveDungeonInstancePayload::controllerPos,
        BlockPos.STREAM_CODEC, RemoveDungeonInstancePayload::instancePos,
        RemoveDungeonInstancePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
