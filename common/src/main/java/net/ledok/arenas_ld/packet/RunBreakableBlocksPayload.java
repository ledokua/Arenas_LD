package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → a player: the blocks their dungeon or raid run lets them break (ids and {@code #tags}), or
 * {@code inRun=false} once they're out of it. See {@link net.ledok.arenas_ld.util.RunBlockBreaking}.
 */
public record RunBreakableBlocksPayload(boolean inRun, List<String> entries) implements CustomPacketPayload {
    public static final Type<RunBreakableBlocksPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_breakable_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RunBreakableBlocksPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, RunBreakableBlocksPayload::inRun,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), RunBreakableBlocksPayload::entries,
        RunBreakableBlocksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
