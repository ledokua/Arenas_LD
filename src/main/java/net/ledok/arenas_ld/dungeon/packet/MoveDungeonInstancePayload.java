package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MoveDungeonInstancePayload(BlockPos controllerPos, int fromIndex, int toIndex) implements CustomPacketPayload {
    public static final Type<MoveDungeonInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_move_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveDungeonInstancePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, MoveDungeonInstancePayload::controllerPos,
        ByteBufCodecs.VAR_INT, MoveDungeonInstancePayload::fromIndex,
        ByteBufCodecs.VAR_INT, MoveDungeonInstancePayload::toIndex,
        MoveDungeonInstancePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
