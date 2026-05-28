package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetTierPayload(BlockPos blockPos, DifficultyTier tier) implements CustomPacketPayload {
    public static final Type<RaidSetTierPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_set_tier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetTierPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidSetTierPayload::blockPos,
            DifficultyTier.STREAM_CODEC, RaidSetTierPayload::tier,
            RaidSetTierPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
