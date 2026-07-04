package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateDbsEntityDefPayload(BlockPos blockPos, String mobId, int wave) implements CustomPacketPayload {
    public static final Type<UpdateDbsEntityDefPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dbs_v2_entity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateDbsEntityDefPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, UpdateDbsEntityDefPayload::blockPos,
        ByteBufCodecs.STRING_UTF8, UpdateDbsEntityDefPayload::mobId,
        ByteBufCodecs.VAR_INT, UpdateDbsEntityDefPayload::wave,
        UpdateDbsEntityDefPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
