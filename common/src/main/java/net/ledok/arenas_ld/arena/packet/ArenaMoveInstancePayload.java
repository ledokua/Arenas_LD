package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Moves the instance at {@code spawnerPos}/{@code dimension} one slot up ({@code direction < 0}) or down. */
public record ArenaMoveInstancePayload(BlockPos controllerPos, BlockPos spawnerPos, String dimension, int direction) implements CustomPacketPayload {
    public static final Type<ArenaMoveInstancePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_move_instance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaMoveInstancePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeBlockPos(p.spawnerPos()); buf.writeUtf(p.dimension()); buf.writeVarInt(p.direction()); },
        buf -> new ArenaMoveInstancePayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
