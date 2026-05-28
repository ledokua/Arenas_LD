package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidRemoveInstancePayload(BlockPos controllerPos, BlockPos instancePos, String dimension) implements CustomPacketPayload {
    public static final Type<RaidRemoveInstancePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_remove_instance")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidRemoveInstancePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeBlockPos(p.instancePos());
            buf.writeUtf(p.dimension());
        },
        buf -> new RaidRemoveInstancePayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
