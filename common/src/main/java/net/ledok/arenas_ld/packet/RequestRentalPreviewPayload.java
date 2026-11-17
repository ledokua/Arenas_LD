package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Lobby screen → server: show me the rental kits (answered with {@link RentalPreviewPayload}). */
public record RequestRentalPreviewPayload(BlockPos controller) implements CustomPacketPayload {
    public static final Type<RequestRentalPreviewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "request_rental_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRentalPreviewPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestRentalPreviewPayload::controller, RequestRentalPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
