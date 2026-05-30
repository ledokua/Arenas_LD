package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArenaMoveInstancePayload(BlockPos controllerPos, int from, int to) implements CustomPacketPayload {
    public static final Type<ArenaMoveInstancePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_move_instance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaMoveInstancePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeVarInt(p.from()); buf.writeVarInt(p.to()); },
        buf -> new ArenaMoveInstancePayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
