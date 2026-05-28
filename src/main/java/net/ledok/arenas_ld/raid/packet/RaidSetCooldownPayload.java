package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetCooldownPayload(BlockPos controllerPos, int cooldownTicks) implements CustomPacketPayload {
    public static final Type<RaidSetCooldownPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_cooldown")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetCooldownPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeVarInt(p.cooldownTicks());
        },
        buf -> new RaidSetCooldownPayload(buf.readBlockPos(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
