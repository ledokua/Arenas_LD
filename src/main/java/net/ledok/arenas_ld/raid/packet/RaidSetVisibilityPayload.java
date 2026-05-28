package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetVisibilityPayload(BlockPos blockPos, LobbyVisibility visibility) implements CustomPacketPayload {
    public static final Type<RaidSetVisibilityPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_set_visibility"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetVisibilityPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidSetVisibilityPayload::blockPos,
            ByteBufCodecs.STRING_UTF8.map(LobbyVisibility::valueOf, Enum::name), RaidSetVisibilityPayload::visibility,
            RaidSetVisibilityPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
