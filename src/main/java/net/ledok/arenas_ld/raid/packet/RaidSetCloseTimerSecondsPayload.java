package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetCloseTimerSecondsPayload(BlockPos controllerPos, int closeTimerSeconds) implements CustomPacketPayload {
    public static final Type<RaidSetCloseTimerSecondsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_close_timer")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetCloseTimerSecondsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeVarInt(p.closeTimerSeconds());
        },
        buf -> new RaidSetCloseTimerSecondsPayload(buf.readBlockPos(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
