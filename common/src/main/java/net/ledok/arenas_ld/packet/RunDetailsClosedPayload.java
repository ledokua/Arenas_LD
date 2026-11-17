package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: the run details screen was closed (stop sending updates). */
public record RunDetailsClosedPayload() implements CustomPacketPayload {
    public static final RunDetailsClosedPayload INSTANCE = new RunDetailsClosedPayload();
    public static final Type<RunDetailsClosedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_details_closed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RunDetailsClosedPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
