package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArenaSetHardcorePayload(BlockPos controllerPos, boolean hardcore) implements CustomPacketPayload {
    public static final Type<ArenaSetHardcorePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_set_hardcore"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSetHardcorePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeBoolean(p.hardcore()); },
        buf -> new ArenaSetHardcorePayload(buf.readBlockPos(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
