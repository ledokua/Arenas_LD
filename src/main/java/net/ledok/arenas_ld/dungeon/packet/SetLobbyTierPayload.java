package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetLobbyTierPayload(BlockPos blockPos, DifficultyTier tier) implements CustomPacketPayload {
    public static final Type<SetLobbyTierPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_set_lobby_tier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLobbyTierPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLobbyTierPayload::blockPos,
            DifficultyTier.STREAM_CODEC, SetLobbyTierPayload::tier,
            SetLobbyTierPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
