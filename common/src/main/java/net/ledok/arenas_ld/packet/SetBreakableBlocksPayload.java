package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Admin screen → server: a dungeon or raid controller's own breakable blocks list ({@code mode}, entries). */
public record SetBreakableBlocksPayload(BlockPos controllerPos, String mode, List<String> blocks) implements CustomPacketPayload {
    public static final Type<SetBreakableBlocksPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "admin_set_breakable_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetBreakableBlocksPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetBreakableBlocksPayload::controllerPos,
        ByteBufCodecs.STRING_UTF8, SetBreakableBlocksPayload::mode,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), SetBreakableBlocksPayload::blocks,
        SetBreakableBlocksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
