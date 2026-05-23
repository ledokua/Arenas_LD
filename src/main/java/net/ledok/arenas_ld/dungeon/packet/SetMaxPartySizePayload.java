package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetMaxPartySizePayload(BlockPos controllerPos, int size) implements CustomPacketPayload {
    public static final Type<SetMaxPartySizePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_max_party_size")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMaxPartySizePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetMaxPartySizePayload::controllerPos,
        ByteBufCodecs.VAR_INT, SetMaxPartySizePayload::size,
        SetMaxPartySizePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
