package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetInviteExpiryTicksPayload(BlockPos controllerPos, int inviteExpiryTicks) implements CustomPacketPayload {
    public static final Type<RaidSetInviteExpiryTicksPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_invite_expiry")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetInviteExpiryTicksPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeVarInt(p.inviteExpiryTicks());
        },
        buf -> new RaidSetInviteExpiryTicksPayload(buf.readBlockPos(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
