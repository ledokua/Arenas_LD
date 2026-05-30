package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArenaRemoveInstancePayload(BlockPos controllerPos, BlockPos spawnerPos, String dimension) implements CustomPacketPayload {
    public static final Type<ArenaRemoveInstancePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_remove_instance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaRemoveInstancePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeBlockPos(p.spawnerPos()); buf.writeUtf(p.dimension()); },
        buf -> new ArenaRemoveInstancePayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
