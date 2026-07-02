package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArenaSetVisibilityPayload(BlockPos controllerPos, String visibility) implements CustomPacketPayload {
    public static final Type<ArenaSetVisibilityPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_set_visibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSetVisibilityPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeUtf(p.visibility()); },
        buf -> new ArenaSetVisibilityPayload(buf.readBlockPos(), buf.readUtf()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
