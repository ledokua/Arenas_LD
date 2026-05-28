package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidAddInstancePayload(BlockPos controllerPos, BlockPos instancePos, String dimension) implements CustomPacketPayload {
    public static final Type<RaidAddInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_add_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidAddInstancePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeBlockPos(p.instancePos());
            buf.writeUtf(p.dimension());
        },
        buf -> new RaidAddInstancePayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
