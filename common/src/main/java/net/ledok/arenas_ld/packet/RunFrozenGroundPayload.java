package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → a run player: the "Frozen ground" modifier starts or stops (their own movement slides). */
public record RunFrozenGroundPayload(boolean frozen) implements CustomPacketPayload {
    public static final Type<RunFrozenGroundPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_frozen_ground"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RunFrozenGroundPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeBoolean(p.frozen()), buf -> new RunFrozenGroundPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
