package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Spawner screen → server: whether the spawner's mobs pass their scaling on to their minions (any
 * Mob, Dungeon Boss, Raid Boss or Arena Spawner). Op only.
 */
public record SetMinionScalingPayload(BlockPos spawnerPos, boolean scale) implements CustomPacketPayload {
    public static final Type<SetMinionScalingPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "set_minion_scaling"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetMinionScalingPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.spawnerPos());
            buf.writeBoolean(p.scale());
        },
        buf -> new SetMinionScalingPayload(buf.readBlockPos(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
