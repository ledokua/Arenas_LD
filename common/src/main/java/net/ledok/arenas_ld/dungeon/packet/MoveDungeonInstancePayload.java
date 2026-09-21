package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Moves the instance at {@code instancePos} one slot up ({@code direction < 0}) or down. */
public record MoveDungeonInstancePayload(BlockPos controllerPos, BlockPos instancePos, int direction) implements CustomPacketPayload {
    public static final Type<MoveDungeonInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_move_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveDungeonInstancePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, MoveDungeonInstancePayload::controllerPos,
        BlockPos.STREAM_CODEC, MoveDungeonInstancePayload::instancePos,
        ByteBufCodecs.VAR_INT, MoveDungeonInstancePayload::direction,
        MoveDungeonInstancePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
