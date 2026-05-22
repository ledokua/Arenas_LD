package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateDbsEntrancePayload(
    BlockPos blockPos,
    int x,
    int y,
    int z,
    String dimension,
    boolean setToPlayer
) implements CustomPacketPayload {
    public static final Type<UpdateDbsEntrancePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dbs_v2_entrance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateDbsEntrancePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, UpdateDbsEntrancePayload::blockPos,
        ByteBufCodecs.VAR_INT, UpdateDbsEntrancePayload::x,
        ByteBufCodecs.VAR_INT, UpdateDbsEntrancePayload::y,
        ByteBufCodecs.VAR_INT, UpdateDbsEntrancePayload::z,
        ByteBufCodecs.STRING_UTF8, UpdateDbsEntrancePayload::dimension,
        ByteBufCodecs.BOOL, UpdateDbsEntrancePayload::setToPlayer,
        UpdateDbsEntrancePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
