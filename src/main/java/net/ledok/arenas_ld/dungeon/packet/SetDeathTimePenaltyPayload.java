package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDeathTimePenaltyPayload(BlockPos controllerPos, int ticks) implements CustomPacketPayload {
    public static final Type<SetDeathTimePenaltyPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_death_penalty")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDeathTimePenaltyPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetDeathTimePenaltyPayload::controllerPos,
        ByteBufCodecs.VAR_INT, SetDeathTimePenaltyPayload::ticks,
        SetDeathTimePenaltyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
