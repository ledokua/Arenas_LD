package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AddDungeonInstancePayload(BlockPos controllerPos, BlockPos instancePos) implements CustomPacketPayload {
    public static final Type<AddDungeonInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_add_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AddDungeonInstancePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AddDungeonInstancePayload::controllerPos,
        BlockPos.STREAM_CODEC, AddDungeonInstancePayload::instancePos,
        AddDungeonInstancePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
