package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidAdminSetRespawnTimePayload(BlockPos blockPos, int ticks) implements CustomPacketPayload {
    public static final Type<RaidAdminSetRespawnTimePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_respawn_time"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidAdminSetRespawnTimePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidAdminSetRespawnTimePayload::blockPos,
            ByteBufCodecs.VAR_INT, RaidAdminSetRespawnTimePayload::ticks,
            RaidAdminSetRespawnTimePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
