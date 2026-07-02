package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArenaSetRewardCurvePayload(BlockPos controllerPos, double currencyBase, double currencyExp,
                                         double xpBase, double xpExp) implements CustomPacketPayload {
    public static final Type<ArenaSetRewardCurvePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_set_reward_curve"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSetRewardCurvePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeDouble(p.currencyBase()); buf.writeDouble(p.currencyExp());
                      buf.writeDouble(p.xpBase()); buf.writeDouble(p.xpExp()); },
        buf -> new ArenaSetRewardCurvePayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
