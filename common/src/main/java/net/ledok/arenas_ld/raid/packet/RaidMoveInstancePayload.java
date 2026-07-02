package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidMoveInstancePayload(BlockPos controllerPos, int fromIndex, int toIndex) implements CustomPacketPayload {
    public static final Type<RaidMoveInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_move_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidMoveInstancePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeVarInt(p.fromIndex());
            buf.writeVarInt(p.toIndex());
        },
        buf -> new RaidMoveInstancePayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
