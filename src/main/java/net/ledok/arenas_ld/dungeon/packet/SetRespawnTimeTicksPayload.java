package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetRespawnTimeTicksPayload(BlockPos controllerPos, int ticks) implements CustomPacketPayload {
    public static final Type<SetRespawnTimeTicksPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_respawn_time_ticks")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRespawnTimeTicksPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetRespawnTimeTicksPayload::controllerPos,
        ByteBufCodecs.VAR_INT, SetRespawnTimeTicksPayload::ticks,
        SetRespawnTimeTicksPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
