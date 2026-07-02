package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetDeathTimePenaltyPayload(BlockPos controllerPos, int ticks) implements CustomPacketPayload {
    public static final Type<RaidSetDeathTimePenaltyPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_death_penalty")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetDeathTimePenaltyPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeVarInt(p.ticks());
        },
        buf -> new RaidSetDeathTimePenaltyPayload(buf.readBlockPos(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
